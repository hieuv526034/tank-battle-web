package vn.edu.utc.tankbattle.business;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import vn.edu.utc.tankbattle.auth.service.*;
import vn.edu.utc.tankbattle.business.config.*;
import vn.edu.utc.tankbattle.business.dto.request.*;
import vn.edu.utc.tankbattle.business.dto.response.*;
import vn.edu.utc.tankbattle.business.engine.*;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.business.runtime.GameCoordinator;
import vn.edu.utc.tankbattle.business.service.*;
import vn.edu.utc.tankbattle.business.support.*;
import vn.edu.utc.tankbattle.business.websocket.SocketHub;
import vn.edu.utc.tankbattle.common.constant.AppConstants.*;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

/**
 * Nghiệm thu BE: JWT/filter/API/transaction và WebSocket TCP thật. Không @Transactional trên test
 * để quan sát commit, callback và tranh chấp nhiều connection.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"tank.max-rooms=1000", "tank.lobby-disconnect-ms=3600000"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class BusinessFlowTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        vn.edu.utc.tankbattle.storage.support.TestDatabaseProperties.configure(
                properties, "business-flow");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate db;
    @Autowired private AccountService accounts;
    @Autowired private TokenService tokens;
    @Autowired private RoomService rooms;
    @Autowired private MatchService matches;
    @Autowired private RankingService rankings;
    @Autowired private OfflineSaveService saves;
    @Autowired private StateCodec codec;
    @Autowired private GameContentLoader content;
    @Autowired private CampaignCatalog campaign;
    @Autowired private GameCoordinator games;
    @LocalServerPort private int port;

    private ApiPlayer player() {
        String username = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        long id =
                accounts.create(
                        username, "Người chơi " + username.substring(0, 6), "test-fixture-hash");
        return new ApiPlayer(
                id, username, tokens.issue(accounts.credentials(username)).access().accessToken());
    }

    private String bearer(ApiPlayer player) {
        return "Bearer " + player.accessToken();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private RoomResponse room(ApiPlayer host, ApiPlayer guest) {
        RoomResponse room = rooms.create(host.id(), "Phòng kiểm thử", 2);
        rooms.join(guest.id(), room.code());
        rooms.ready(host.id(), room.code(), true);
        rooms.ready(guest.id(), room.code(), true);
        return rooms.byCode(room.code());
    }

    private GameState begin(RoomResponse room, ApiPlayer host, GameMode mode, int stage) {
        List<PlayerState> roster =
                room.members().stream()
                        .map(
                                member ->
                                        new PlayerState(
                                                Long.toString(member.userId()),
                                                member.displayName(),
                                                3,
                                                0,
                                                0))
                        .toList();
        GameState state =
                GameEngine.create(
                        UUID.randomUUID().toString(),
                        codec.rules(),
                        roster,
                        content.definition(),
                        mode,
                        Difficulty.NORMAL,
                        mode == GameMode.CAMPAIGN ? campaign.stage(stage) : null);
        matches.begin(room, host.id(), state);
        return state;
    }

    @Test
    void roomApiValidatesCapacityMembershipHostTransferAndPageErrors() throws Exception {
        ApiPlayer host = player(), guest = player(), outsider = player();
        mvc.perform(
                        post("/api/rooms")
                                .header(HttpHeaders.AUTHORIZATION, bearer(host))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Phòng học\",\"capacity\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity.code").value("ROOM_003"));
        RoomResponse room = rooms.create(host.id(), "Phòng học UTC", 2);
        mvc.perform(
                        post("/api/rooms/{code}/join", room.code())
                                .header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/rooms/{code}", room.code())
                                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_006"));
        mvc.perform(get("/api/rooms/current").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(jsonPath("$.data.id").value(room.id()));
        mvc.perform(
                        delete("/api/rooms/{code}/members/me", room.code())
                                .header(HttpHeaders.AUTHORIZATION, bearer(host)))
                .andExpect(status().isOk());
        assertThat(rooms.byCode(room.code()).hostUserId()).isEqualTo(guest.id());
        mvc.perform(get("/api/rankings?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page.code").value("PAGE_001"));
        mvc.perform(
                        get("/api/rankings/campaign?difficulty=INVALID")
                                .header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));
        rooms.leave(guest.id(), room.code());
        assertThat(rooms.byCode(room.code()).status()).isEqualTo("CLOSED");
    }

    @Test
    void concurrentJoinCannotOverbookLastSeat() throws Exception {
        ApiPlayer host = player(), first = player(), second = player();
        RoomResponse room = rooms.create(host.id(), "Tranh ghế cuối", 2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<String>> attempts =
                    List.of(
                            pool.submit(() -> joinResult(first.id(), room.code(), start)),
                            pool.submit(() -> joinResult(second.id(), room.code(), start)));
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> attempt : attempts) outcomes.add(attempt.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "ROOM_004");
        }
        RoomResponse finalRoom = rooms.byCode(room.code());
        assertThat(finalRoom.members()).hasSize(2);
        assertThat(finalRoom.members().stream().map(MemberResponse::seat).distinct().count())
                .isEqualTo(2);
    }

    private String joinResult(long userId, String code, CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            rooms.join(userId, code);
            return "OK";
        } catch (BusinessException rejected) {
            return rejected.getValidateCode().getCode();
        }
    }

    @Test
    void sameUserCannotJoinTwoRoomsConcurrently() throws Exception {
        ApiPlayer firstHost = player(), secondHost = player(), guest = player();
        RoomResponse first = rooms.create(firstHost.id(), "Phòng thứ nhất", 2),
                second = rooms.create(secondHost.id(), "Phòng thứ hai", 2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> a = pool.submit(() -> joinResult(guest.id(), first.code(), start));
            Future<String> b = pool.submit(() -> joinResult(guest.id(), second.code(), start));
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "ROOM_007");
        }
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM room_players WHERE user_id=?",
                                Integer.class,
                                guest.id()))
                .isEqualTo(1);
    }

    @Test
    void finalizationIsIdempotentAndHistoryIsPrivateAndPayloadFree() throws Exception {
        ApiPlayer host = player(), guest = player(), outsider = player();
        RoomResponse room = room(host, guest);
        GameState state = begin(room, host, GameMode.ARENA, 1);
        GameEngine.forfeit(state, Long.toString(guest.id()));
        GameEngine.evaluate(state);
        state.players.get(Long.toString(host.id())).kills = 1;
        state.players.get(Long.toString(host.id())).score = 100;
        GameState stale = codec.copy(state);
        matches.finish(state);
        matches.finish(stale);
        matches.checkpoint(stale);
        assertThat(rankings.personal(host.id()).orElseThrow().points()).isEqualTo(3);
        assertThat(
                        matches.results(state.matchId).stream()
                                .filter(result -> result.userId() == host.id())
                                .findFirst()
                                .orElseThrow()
                                .score())
                .isEqualTo(100);
        assertThat(codec.read(matches.get(state.matchId).payload()).phase).isEqualTo("FINISHED");
        mvc.perform(
                        get("/api/matches/{id}", state.matchId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATCH_002"));
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].payload").doesNotExist())
                .andExpect(jsonPath("$.data[0].mode").value("ARENA"));
        mvc.perform(
                        get("/api/matches/{id}/state", state.matchId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(host)))
                .andExpect(jsonPath("$.mode").doesNotExist())
                .andExpect(jsonPath("$.data.mode").value("FINISHED"));
    }

    @Test
    void checkpointsAreMonotonicAndForfeitPersistsAcrossRecoveryWithoutExtendingDeadline() {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        GameState state = begin(room, host, GameMode.ARENA, 1);
        state.tick = 20;
        matches.checkpoint(state);
        state.tick = 10;
        matches.checkpoint(state);
        assertThat(codec.read(matches.get(state.matchId).payload()).tick).isEqualTo(20);
        rooms.leave(guest.id(), room.code());
        GameState restored = codec.read(matches.get(state.matchId).payload());
        matches.applyDurableForfeits(restored);
        assertThat(restored.players.get(Long.toString(guest.id())).forfeited).isTrue();
        matches.prepareRecovery(List.of(matches.get(state.matchId)), 60000);
        Instant deadline = matches.get(state.matchId).recoveryDeadline();
        matches.prepareRecovery(List.of(matches.get(state.matchId)), 999999);
        assertThat(matches.get(state.matchId).recoveryDeadline()).isEqualTo(deadline);
        matches.resume(state.matchId);
        assertThat(matches.get(state.matchId).status()).isEqualTo("RUNNING");
        matches.abort(state.matchId, "TEST_END");
        assertThat(rankings.personal(host.id())).isEmpty();
    }

    @Test
    void campaignRequiresUnlockAndUsesBestScorePerStageWithoutAffectingArenaRank() {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        assertThatThrownBy(() -> begin(room, host, GameMode.CAMPAIGN, 2))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error ->
                                assertThat(error.getValidateCode())
                                        .isEqualTo(ValidateCode.GAME_STAGE_LOCKED));
        GameState first = begin(room, host, GameMode.CAMPAIGN, 1);
        first.players.get(Long.toString(host.id())).score = 150;
        first.endReason = "CAMPAIGN_CLEARED";
        first.phase = "ENDING";
        matches.finish(first);
        assertThat(rankings.personal(host.id())).isEmpty();
        assertThat(
                        rankings.campaign("NORMAL", 0, 50).stream()
                                .filter(rank -> rank.userId() == host.id())
                                .findFirst()
                                .orElseThrow()
                                .score())
                .isEqualTo(150);
        assertThat(rankings.clearedStages(host.id(), "NORMAL")).containsExactly(1);
        assertThat(rankings.campaignPersonal(host.id(), "NORMAL").orElseThrow().score())
                .isEqualTo(150);
        rooms.ready(host.id(), room.code(), true);
        rooms.ready(guest.id(), room.code(), true);
        GameState second = begin(rooms.byCode(room.code()), host, GameMode.CAMPAIGN, 2);
        second.players.get(Long.toString(host.id())).score = 80;
        second.endReason = "CAMPAIGN_CLEARED";
        second.phase = "ENDING";
        matches.finish(second);
        assertThat(
                        rankings.campaign("NORMAL", 0, 50).stream()
                                .filter(rank -> rank.userId() == host.id())
                                .findFirst()
                                .orElseThrow()
                                .score())
                .isEqualTo(230);
    }

    @Test
    void offlineSaveUsesRevisionAndOwnershipAndCannotAffectOnlineRanking() throws Exception {
        ApiPlayer owner = player(), other = player();
        GameState state =
                GameEngine.create(
                        UUID.randomUUID().toString(),
                        codec.rules(),
                        List.of(
                                new PlayerState("local", "A", 3, 0, 0),
                                new PlayerState("bot", "Bot", 3, 0, 0)));
        state.players.get("local").score = 999999;
        OfflineSaveRequest request = new OfflineSaveRequest();
        request.setState(state);
        OfflineSaveResponse stored = saves.put(owner.id(), 1, request);
        assertThat(stored.revision()).isEqualTo(1);
        assertThatThrownBy(() -> saves.put(owner.id(), 1, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error ->
                                assertThat(error.getValidateCode())
                                        .isEqualTo(ValidateCode.SAVE_REVISION_CONFLICT));
        mvc.perform(get("/api/offline-saves/1").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SAVE_001"));
        assertThat(saves.list(owner.id()).getFirst().state()).isNull();
        assertThat(rankings.personal(owner.id())).isEmpty();
        request.setExpectedRevision(1);
        assertThat(saves.put(owner.id(), 1, request).revision()).isEqualTo(2);
        saves.delete(owner.id(), 1, 2);
        assertThat(saves.list(owner.id())).isEmpty();
        request.setExpectedRevision(0);
        assertThat(saves.put(owner.id(), 1, request).revision()).isEqualTo(4);
        // Thiết bị cũ giữ revision=1 không thể ghi đè slot mới vừa tái tạo.
        request.setExpectedRevision(1);
        assertThatThrownBy(() -> saves.put(owner.id(), 1, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error ->
                                assertThat(error.getValidateCode())
                                        .isEqualTo(ValidateCode.SAVE_REVISION_CONFLICT));
    }

    @Test
    void websocketTicketIsOneUseRejectsCrossOriginAndDrivesStartSyncAndLogout() throws Exception {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        StandardWebSocketClient client = new StandardWebSocketClient();
        SocketProbe hostProbe = new SocketProbe(), guestProbe = new SocketProbe();
        String hostTicket = ticket(host), guestTicket = ticket(guest);
        WebSocketSession hostSocket =
                client.execute(
                                hostProbe,
                                new WebSocketHttpHeaders(),
                                URI.create(socketUrl(hostTicket)))
                        .get(8, TimeUnit.SECONDS);
        WebSocketSession guestSocket =
                client.execute(
                                guestProbe,
                                englishSocketHeaders(),
                                URI.create(socketUrl(guestTicket)))
                        .get(8, TimeUnit.SECONDS);
        try {
            hostProbe.await("LOBBY");
            guestProbe.await("LOBBY");
            assertThatThrownBy(
                            () ->
                                    client.execute(
                                                    new SocketProbe(),
                                                    new WebSocketHttpHeaders(),
                                                    URI.create(socketUrl(hostTicket)))
                                            .get(8, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
            WebSocketHttpHeaders evilOrigin = new WebSocketHttpHeaders();
            evilOrigin.setOrigin("https://attacker.invalid");
            String unusedTicket = ticket(host);
            assertThatThrownBy(
                            () ->
                                    client.execute(
                                                    new SocketProbe(),
                                                    evilOrigin,
                                                    URI.create(socketUrl(unusedTicket)))
                                            .get(8, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
            String matchId =
                    body(mvc.perform(
                                            post("/api/rooms/{code}/start", room.code())
                                                    .header(
                                                            HttpHeaders.AUTHORIZATION,
                                                            bearer(host)))
                                    .andExpect(status().isOk())
                                    .andReturn())
                            .at("/data/matchId")
                            .asText();
            hostSocket.sendMessage(
                    new TextMessage(
                            "{\"type\":\"input\",\"matchId\":\""
                                    + matchId
                                    + "\",\"sequence\":1,\"direction\":\"RIGHT\",\"fire\":true}"));
            hostSocket.sendMessage(
                    new TextMessage("{\"type\":\"sync\",\"matchId\":\"" + matchId + "\"}"));
            assertThat(hostProbe.await("GAME").at("/data/state/matchId").asText())
                    .isEqualTo(matchId);
            hostSocket.sendMessage(new TextMessage("{bad"));
            JsonNode vietnameseError = hostProbe.await("ERROR").path("data");
            assertThat(vietnameseError.path("code").asText()).isEqualTo("SYS_003");
            assertThat(vietnameseError.path("locale").asText()).isEqualTo("vi");
            assertThat(vietnameseError.path("message").asText())
                    .isEqualTo("JSON sai định dạng hoặc sai kiểu dữ liệu.");
            guestSocket.sendMessage(new TextMessage("{bad"));
            JsonNode englishError = guestProbe.await("ERROR").path("data");
            assertThat(englishError.path("code").asText()).isEqualTo("SYS_003");
            assertThat(englishError.path("locale").asText()).isEqualTo("en");
            assertThat(englishError.path("message").asText())
                    .isEqualTo("Malformed JSON or invalid field type.");
            // Thu hồi service thật phát sự kiện sau commit, handler đóng socket hiện tại.
            tokens.revokeAll(host.id());
            assertThat(hostProbe.closed.await(5, TimeUnit.SECONDS)).isTrue();
            games.leave(host.id(), room.code());
            games.leave(guest.id(), room.code());
        } finally {
            hostSocket.close();
            guestSocket.close();
        }
    }

    private String socketUrl(String ticket) {
        return "ws://localhost:" + port + "/ws/game?ticket=" + ticket;
    }

    private WebSocketHttpHeaders englishSocketHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setAcceptLanguageAsLocales(List.of(Locale.ENGLISH));
        return headers;
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "vi|vi|Phòng không tồn tại.",
                "en|en|Room not found.",
                "en-US|en|Room not found.",
                "fr|vi|Phòng không tồn tại.",
                "fr;q=1.0,en;q=0.8|en|Room not found.",
                "DEFAULT|vi|Phòng không tồn tại."
            },
            delimiter = '|')
    void languageHeaderAppliesToBusinessSecurityValidationAndSuccess(
            String requested, String expectedLocale, String roomMessage) throws Exception {
        ApiPlayer actor = player();
        MockHttpServletRequestBuilder missingRoom =
                get("/api/rooms/ZZZZ2222").header(HttpHeaders.AUTHORIZATION, bearer(actor));
        MockHttpServletRequestBuilder denied = get("/api/auth/me");
        MockHttpServletRequestBuilder validation =
                get("/api/rankings?page=-1").header(HttpHeaders.AUTHORIZATION, bearer(actor));
        MockHttpServletRequestBuilder success =
                get("/api/game/catalog").header(HttpHeaders.AUTHORIZATION, bearer(actor));
        if (!"DEFAULT".equals(requested)) {
            missingRoom.header(HttpHeaders.ACCEPT_LANGUAGE, requested);
            denied.header(HttpHeaders.ACCEPT_LANGUAGE, requested);
            validation.header(HttpHeaders.ACCEPT_LANGUAGE, requested);
            success.header(HttpHeaders.ACCEPT_LANGUAGE, requested);
        }
        mvc.perform(missingRoom)
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, expectedLocale))
                .andExpect(jsonPath("$.code").value("ROOM_001"))
                .andExpect(jsonPath("$.locale").value(expectedLocale))
                .andExpect(jsonPath("$.message").value(roomMessage));
        mvc.perform(denied)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, expectedLocale))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.locale").value(expectedLocale))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "en".equals(expectedLocale)
                                                ? "Please sign in to continue."
                                                : "Bạn cần đăng nhập để tiếp tục."));
        mvc.perform(validation)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.locale").value(expectedLocale))
                .andExpect(jsonPath("$.errors.page.code").value("PAGE_001"))
                .andExpect(
                        jsonPath("$.errors.page.message")
                                .value(
                                        "en".equals(expectedLocale)
                                                ? "Page must be 0–10000 and page size must be 1–50."
                                                : "Trang cần từ 0 đến 10000; kích thước từ 1 đến 50."));
        mvc.perform(success)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value(expectedLocale))
                .andExpect(
                        jsonPath("$.message")
                                .value("en".equals(expectedLocale) ? "Success" : "Thành công"));
    }

    @Test
    void simultaneousFinalDeathsReceiveDrawPointsAndConcurrentFinishCannotDoubleCount()
            throws Exception {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        GameState state = begin(room, host, GameMode.ARENA, 1);
        state.tick = 100;
        state.players
                .values()
                .forEach(
                        player -> {
                            player.hp = 0;
                            player.eliminatedAtTick = 100;
                        });
        state.phase = "ENDING";
        state.endReason = "MUTUAL_ELIMINATION";
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first =
                    pool.submit(
                            () -> {
                                try {
                                    start.await();
                                    matches.finish(codec.copy(state));
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(interrupted);
                                }
                            });
            Future<?> second =
                    pool.submit(
                            () -> {
                                try {
                                    start.await();
                                    matches.finish(codec.copy(state));
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(interrupted);
                                }
                            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(matches.results(state.matchId))
                .allMatch(result -> "DRAW".equals(result.outcome()));
        assertThat(rankings.personal(host.id()).orElseThrow().points()).isEqualTo(1);
        assertThat(rankings.personal(host.id()).orElseThrow().played()).isEqualTo(1);
    }

    /** Khách đọc bảng hạng công khai; hạng/tiến độ riêng và nghiệp vụ phòng vẫn cần JWT. */
    @Test
    void rankingListsArePublicButPersonalDataStillRequiresAuthentication() throws Exception {
        for (String path : List.of("/api/rankings", "/api/rankings/campaign")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
        for (String path :
                List.of(
                        "/api/rankings/me",
                        "/api/rankings/campaign/me",
                        "/api/rankings/campaign/me/progress",
                        "/api/rooms")) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));
        }
        JsonNode specification =
                body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        for (String path : List.of("/api/rankings", "/api/rankings/campaign")) {
            JsonNode security = specification.path("paths").path(path).path("get").path("security");
            assertThat(security.isMissingNode() || security.isEmpty()).isTrue();
        }
        JsonNode personalSecurity = specification.at("/paths/~1api~1rankings~1me/get/security");
        assertThat(personalSecurity.get(0).has("bearerAuth")).isTrue();
    }

    @Test
    void profileAndOpenApiExposeActualBusinessContracts() throws Exception {
        ApiPlayer owner = player();
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(owner.username()));
        mvc.perform(
                        post("/api/auth/socket-ticket")
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
        JsonNode specification =
                body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(specification.path("paths").has("/api/offline-saves/{slot}")).isTrue();
        assertThat(specification.path("paths").has("/api/rooms/{code}/start")).isTrue();
        JsonNode security =
                specification
                        .path("paths")
                        .path("/api/auth/socket-ticket")
                        .path("post")
                        .path("security");
        assertThat(security.size()).isEqualTo(1);
        assertThat(security.get(0).has("bearerAuth") && security.get(0).has("csrfHeader")).isTrue();
        JsonNode languageParameter = specification.at("/components/parameters/acceptLanguage");
        assertThat(languageParameter.path("name").asText()).isEqualTo("Accept-Language");
        assertThat(languageParameter.path("in").asText()).isEqualTo("header");
        assertThat(languageParameter.path("required").asBoolean()).isFalse();
        assertThat(languageParameter.at("/schema/default").asText()).isEqualTo("vi");
        assertThat(languageParameter.at("/schema/enum"))
                .isEqualTo(json.valueToTree(List.of("vi", "en")));
        int operations = 0;
        Iterator<Map.Entry<String, JsonNode>> paths = specification.path("paths").fields();
        Set<String> methods =
                Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            if (!path.getKey().startsWith("/api/") && !path.getKey().equals("/auth/token"))
                continue;
            Iterator<Map.Entry<String, JsonNode>> entries = path.getValue().fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> operation = entries.next();
                if (!methods.contains(operation.getKey())) continue;
                long declared = 0;
                for (JsonNode parameter : operation.getValue().path("parameters"))
                    if ("#/components/parameters/acceptLanguage"
                            .equals(parameter.path("$ref").asText())) declared++;
                assertThat(declared).as(path.getKey() + " " + operation.getKey()).isEqualTo(1);
                operations++;
            }
        }
        assertThat(operations).isGreaterThanOrEqualTo(34);
    }

    /** Cả API lẫn DB giữ nguyên tên/username/quyền khi client cũ cố gửi PUT sửa hồ sơ. */
    @Test
    void profileIsReadOnlyAndRejectsUpdatesWithLocalizedCode() throws Exception {
        ApiPlayer owner = player();
        Map<String, Object> before =
                db.queryForMap(
                        "SELECT username,display_name,role,password_hash,updated_at FROM users WHERE id=?",
                        owner.id());
        for (String locale : List.of("vi", "en")) {
            mvc.perform(
                            put("/api/profile")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                                    .header(HttpHeaders.ACCEPT_LANGUAGE, locale)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"displayName\":\"Tên khác\",\"username\":\"other_user\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_007"))
                    .andExpect(jsonPath("$.messageKey").value("user.profile.read_only"))
                    .andExpect(jsonPath("$.locale").value(locale))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        assertThat(
                        db.queryForMap(
                                "SELECT username,display_name,role,password_hash,updated_at FROM users WHERE id=?",
                                owner.id()))
                .isEqualTo(before);
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
        String adminToken = tokens.issue(accounts.credentials("admin")).access().accessToken();
        String adminName = accounts.byUsername("admin").displayName();
        mvc.perform(
                        put("/api/profile")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"Tên quản trị khác\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_007"));
        assertThat(accounts.byUsername("admin").displayName()).isEqualTo(adminName);
    }

    @Test
    void newCoordinatorRestoresPausedMatchAndRequiresAllAlivePlayersBeforeResuming()
            throws Exception {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        GameState state = begin(room, host, GameMode.CAMPAIGN, 1);
        state.tick = 45;
        state.players
                .get(Long.toString(host.id()))
                .buffs
                .put(ItemType.SPEED, new BuffState(25, 200));
        Instant initialDeadline = matches.pause(state, 60000);
        SocketHub presence = org.mockito.Mockito.mock(SocketHub.class);
        org.mockito.Mockito.when(
                        presence.connected(
                                org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        GameCoordinator restarted =
                new GameCoordinator(
                        rooms, matches, codec, presence, campaign, content, 2000, 30000, 60000,
                        60000);
        try {
            restarted.recover();
            GameFrameResponse frame = restarted.sync(host.id(), state.matchId);
            assertThat(frame.mode()).isEqualTo(RuntimeMode.RECOVERING);
            assertThat(frame.state().tick).isEqualTo(45);
            assertThat(
                            frame.state()
                                    .players
                                    .get(Long.toString(host.id()))
                                    .buffs
                                    .get(ItemType.SPEED)
                                    .expiresAtTick())
                    .isEqualTo(200);
            assertThat(frame.recoveryDeadline()).isEqualTo(initialDeadline);
            restarted.resumeReady(host.id(), state.matchId);
            assertThat(matches.get(state.matchId).status()).isEqualTo("RECOVERING");
            restarted.resumeReady(guest.id(), state.matchId);
            assertThat(matches.get(state.matchId).status()).isEqualTo("RUNNING");
            // Tất cả đóng trình duyệt: tick không mô phỏng tiếp mà đưa snapshot sang executor lưu.
            org.mockito.Mockito.when(
                            presence.connected(
                                    org.mockito.ArgumentMatchers.anyLong(),
                                    org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(false);
            restarted.tick();
        } finally {
            restarted.shutdown();
        }
        assertThat(matches.get(state.matchId).status()).isEqualTo("RECOVERING");
        assertThat(codec.read(matches.get(state.matchId).payload()).tick).isEqualTo(45);
        matches.abort(state.matchId, "TEST_END");
    }

    @Test
    void expiredRecoveryCannotResumeOrExtendItsDeadline() {
        ApiPlayer host = player(), guest = player();
        RoomResponse room = room(host, guest);
        GameState state = begin(room, host, GameMode.ARENA, 1);
        matches.pause(state, 60000);
        Instant expired =
                Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        db.update(
                "UPDATE matches SET recovery_deadline_at=? WHERE id=?",
                JdbcTimeUtils.toDatabase(expired),
                state.matchId);
        matches.prepareRecovery(List.of(matches.get(state.matchId)), 600000);
        assertThat(matches.get(state.matchId).recoveryDeadline())
                .isEqualTo(expired.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThatThrownBy(() -> matches.resume(state.matchId))
                .isInstanceOf(BusinessException.class);
        assertThat(matches.get(state.matchId).status()).isEqualTo("RECOVERING");
        matches.abort(state.matchId, "RECOVERY_EXPIRED");
        assertThat(rankings.personal(host.id())).isEmpty();
    }

    private String ticket(ApiPlayer player) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = body(csrf).at("/data/token").asText();
        return body(mvc.perform(
                                post("/api/auth/socket-ticket")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                                        .cookie(cookie)
                                        .header("X-XSRF-TOKEN", token))
                        .andExpect(status().isOk())
                        .andReturn())
                .at("/data/ticket")
                .asText();
    }
}
