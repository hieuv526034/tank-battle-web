package vn.edu.utc.tankbattle.business.runtime;

import static vn.edu.utc.tankbattle.common.exception.BusinessException.of;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.edu.utc.tankbattle.business.config.CampaignCatalog;
import vn.edu.utc.tankbattle.business.config.GameContentLoader;
import vn.edu.utc.tankbattle.business.dto.request.StartMatchRequest;
import vn.edu.utc.tankbattle.business.dto.response.GameFrameResponse;
import vn.edu.utc.tankbattle.business.dto.response.MatchResponse;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomRuntimeResponse;
import vn.edu.utc.tankbattle.business.dto.response.SocketEvent;
import vn.edu.utc.tankbattle.business.engine.GameEngine;
import vn.edu.utc.tankbattle.business.engine.StateCodec;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.business.model.PlayerInput;
import vn.edu.utc.tankbattle.business.model.PlayerState;
import vn.edu.utc.tankbattle.business.service.MatchService;
import vn.edu.utc.tankbattle.business.service.RoomService;
import vn.edu.utc.tankbattle.business.websocket.SocketHub;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.RuntimeMode;
import vn.edu.utc.tankbattle.common.constant.AppConstants.SocketType;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/**
 * Nối REST/WebSocket với runtime trong RAM và các service lưu DB: bắt đầu trận, nhận input, tick,
 * phát frame, pause/resume và phục hồi sau startup. Một instance giữ quyền điều phối toàn bộ trận.
 *
 * <p>Tick/input cùng khóa từng GameRuntime. Ghi checkpoint/kết quả đi qua executor một luồng có
 * queue giới hạn rồi gọi MatchService transaction; không save/findById theo từng xe trong tick. Các
 * callback cập nhật mode/cờ queue sau I/O để người chơi thấy trạng thái lưu thực tế.
 */
@Service
public class GameCoordinator {
    private static final Logger log = LoggerFactory.getLogger(GameCoordinator.class);
    private final RoomService rooms;
    private final MatchService matches;
    private final StateCodec codec;
    private final SocketHub sockets;
    private final CampaignCatalog campaign;
    private final GameContentLoader content;
    private final long checkpointMs;
    private final long reconnectMs;
    private final long lobbyDisconnectMs;
    private final long recoveryMs;
    private final ConcurrentMap<String, GameRuntime> runtimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> roomMatches = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> lobbyMissing = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor persistence =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64));

    /**
     * Lắp các service DB, codec, socket hub, catalog và thời hạn vận hành cho bộ điều phối. Runtime
     * được giữ trong RAM; executor persistence một luồng/queue giới hạn sẽ nhận snapshot để ghi DB
     * ở ngoài tick. Các tham số thời gian lấy từ application.yml, đơn vị millisecond.
     */
    public GameCoordinator(
            RoomService rooms,
            MatchService matches,
            StateCodec codec,
            SocketHub sockets,
            CampaignCatalog campaign,
            GameContentLoader content,
            @Value("${tank.checkpoint-ms}") long checkpointMs,
            @Value("${tank.reconnect-ms}") long reconnectMs,
            @Value("${tank.lobby-disconnect-ms}") long lobbyDisconnectMs,
            @Value("${tank.recovery-ms}") long recoveryMs) {
        this.rooms = rooms;
        this.matches = matches;
        this.codec = codec;
        this.sockets = sockets;
        this.campaign = campaign;
        this.content = content;
        this.checkpointMs = checkpointMs;
        this.reconnectMs = reconnectMs;
        this.lobbyDisconnectMs = lobbyDisconnectMs;
        this.recoveryMs = recoveryMs;
    }

    /**
     * Nạp lại các trận chưa xong sau ApplicationReadyEvent, trước khi nhóm xác nhận chơi tiếp. Đọc
     * unfinished/forfeit theo lô, batch đặt RECOVERING, đọc lại deadline đã commit rồi dựng
     * GameRuntime từ checkpoint. Đối chiếu bỏ cuộc bền vững; snapshot đã kết thúc được đưa đi kết
     * toán, snapshot hỏng bị ABORTED. Khôi phục giữ nguyên matchId và deadline, không tạo một trận
     * mới hay gia hạn mỗi lần restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<MatchResponse> unfinished = matches.unfinished();
        Map<String, List<String>> forfeits =
                matches.allForfeits(unfinished.stream().map(MatchResponse::id).toList());
        matches.prepareRecovery(unfinished, recoveryMs);
        Instant fallbackDeadline = Instant.now().plusMillis(recoveryMs);
        // Đọc lại theo lô để runtime dùng đúng deadline đã commit, không tạo một mốc mới lệch DB.
        for (MatchResponse match : matches.unfinished()) {
            try {
                GameState state = codec.read(match.payload());
                if (!state.matchId.equals(match.id()))
                    throw new IllegalArgumentException("Sai matchId");
                forfeits.getOrDefault(match.id(), List.of())
                        .forEach(id -> GameEngine.forfeit(state, id));
                GameEngine.evaluate(state);
                GameRuntime runtime = new GameRuntime(match.roomId(), match.roomCode(), state);
                runtime.mode = RuntimeMode.RECOVERING;
                runtime.recoveryDeadline =
                        match.recoveryDeadline() == null
                                ? fallbackDeadline
                                : match.recoveryDeadline();
                runtimes.put(match.id(), runtime);
                roomMatches.put(match.roomId(), match.id());
                if (!state.phase.equals("RUNNING")) queueFinish(runtime, null);
                log.info("Khôi phục checkpoint matchId={} tick={}", match.id(), state.tick);
            } catch (RuntimeException exception) {
                log.error("Checkpoint không dùng được, matchId={}", match.id(), exception);
                matches.abort(match.id(), "RECOVERY_FAILED");
            }
        }
    }

    /**
     * Bắt đầu trận với lựa chọn mặc định ARENA/NORMAL/1 cho caller không truyền request. Ủy quyền
     * sang overload có StartMatchRequest để cùng kiểm tra roster/kết nối và cùng luồng lưu DB.
     */
    public synchronized String start(long userId, String code) {
        return start(userId, code, new StartMatchRequest());
    }

    /**
     * Điều phối start từ API: đọc phòng, kiểm tra membership và tất cả socket còn kết nối rồi tạo
     * state từ catalog. MatchService.begin khóa/validate lại và commit match + roster + snapshot;
     * sau khi trả về mới đăng ký runtime và phát lobby. synchronized ngăn hai lệnh start trong cùng
     * process cùng đăng ký runtime; khóa DB vẫn bảo vệ trạng thái bền vững.
     *
     * @return matchId để client gửi input/sync và dùng lại khi khôi phục
     */
    public synchronized String start(long userId, String code, StartMatchRequest request) {
        RoomResponse room = rooms.byCode(code);
        rooms.requireMember(room, userId);
        if (room.members().stream().anyMatch(p -> !sockets.connected(p.userId(), room.id()))) {
            throw of(ValidateCode.ROOM_NOT_CONNECTED);
        }
        List<PlayerState> players =
                room.members().stream()
                        .map(
                                p ->
                                        new PlayerState(
                                                Long.toString(p.userId()),
                                                p.displayName(),
                                                3,
                                                0,
                                                0))
                        .toList();
        if (request.getMode() == AppConstants.GameMode.ARENA && request.getStage() != 1)
            throw of(ValidateCode.GAME_INVALID_INPUT);
        GameState state =
                GameEngine.create(
                        UUID.randomUUID().toString(),
                        codec.rules(),
                        players,
                        content.definition(),
                        request.getMode(),
                        request.getDifficulty(),
                        request.getMode() == AppConstants.GameMode.CAMPAIGN
                                ? campaign.stage(request.getStage())
                                : null);
        matches.begin(room, userId, state);
        runtimes.put(state.matchId, new GameRuntime(room.id(), room.code(), state));
        roomMatches.put(room.id(), state.matchId);
        publishRoom(code);
        return state.matchId;
    }

    /**
     * Ghép dữ liệu phòng ở DB với presence, matchId và danh sách xác nhận phục hồi trong RAM. Giữ
     * monitor runtime khi sao chép thông tin đang thay đổi; response giúp lobby biết ai online và
     * có trận cần tiếp tục.
     */
    public RoomRuntimeResponse roomState(String code) {
        RoomResponse room = rooms.byCode(code);
        String matchId = roomMatches.get(room.id());
        GameRuntime runtime = matchId == null ? null : runtimes.get(matchId);
        if (runtime == null)
            return new RoomRuntimeResponse(room, null, sockets.users(room.id()), List.of(), null);
        synchronized (runtime) {
            return new RoomRuntimeResponse(
                    room,
                    matchId,
                    sockets.users(room.id()),
                    List.copyOf(runtime.resumeReady),
                    runtime.recoveryDeadline);
        }
    }

    /**
     * Trả frame đồng bộ cho người từng tham gia trận khi reconnect hoặc gọi GET state. Nếu có
     * runtime thì kiểm tra roster ngay trong RAM và chụp state; nếu đã terminal thì kiểm tra quyền
     * DB rồi đọc snapshot. Frame là dữ liệu để client vẽ, không phải payload được phép gửi ngược
     * lên để ghi kết quả.
     */
    public GameFrameResponse sync(long userId, String matchId) {
        GameRuntime runtime = runtimes.get(matchId);
        if (runtime != null) {
            synchronized (runtime) {
                if (!runtime.state.players.containsKey(Long.toString(userId)))
                    throw of(ValidateCode.MATCH_NOT_PARTICIPANT);
                return frame(runtime);
            }
        }
        MatchResponse stored = matches.get(matchId);
        matches.requireParticipant(matchId, userId);
        GameState state = codec.read(stored.payload());
        if (!Set.of("FINISHED", "ABORTED").contains(stored.status()))
            throw of(ValidateCode.MATCH_NOT_ACTIVE);
        return new GameFrameResponse(
                state,
                RuntimeMode.valueOf(stored.status()),
                List.of(),
                stored.recoveryDeadline(),
                "");
    }

    /**
     * Phát một event LOBBY chứa trạng thái phòng hiện tại cho các socket của room. Caller gọi sau
     * khi service ghi DB đã commit để client nhận roster/ready đã có hiệu lực.
     */
    public void publishRoom(String code) {
        RoomRuntimeResponse state = roomState(code);
        sockets.broadcast(state.room().id(), new SocketEvent<>(SocketType.LOBBY, state));
    }

    /**
     * Cập nhật presence khi handler vừa bind socket mới nhất của user. Xóa mốc mất kết nối và
     * sequence/input cũ, gửi frame hiện tại nếu đang có trận, rồi phát lại lobby. Sequence được
     * reset theo kết nối để client mới mở lại có thể bắt đầu bộ đếm từ đầu.
     */
    public void connected(long userId, String code) {
        lobbyMissing.remove(userId);
        GameRuntime runtime = active(rooms.byCode(code).id());
        if (runtime != null)
            synchronized (runtime) {
                String id = Long.toString(userId);
                runtime.disconnectedAt.remove(id);
                runtime.sequences.remove(id);
                runtime.inputs.remove(id);
                sockets.send(userId, new SocketEvent<>(SocketType.GAME, frame(runtime)));
            }
        publishRoom(code);
    }

    /**
     * Ghi mốc mất kết nối và bỏ input/ready của user trong các runtime RAM liên quan. Handler chỉ
     * gọi nếu socket vừa đóng vẫn là socket mới nhất; callback socket cũ không được làm user đang
     * online thành offline. Tick sẽ quyết định grace/forfeit hoặc pause toàn nhóm, hàm này không tự
     * kết toán.
     */
    public void disconnected(long userId) {
        lobbyMissing.put(userId, System.currentTimeMillis());
        // Tìm trong RAM, không truy vấn room cho từng callback socket.
        for (GameRuntime runtime : runtimes.values())
            synchronized (runtime) {
                String id = Long.toString(userId);
                if (runtime.state.players.containsKey(id)) {
                    runtime.inputs.remove(id);
                    runtime.disconnectedAt.put(id, System.currentTimeMillis());
                    runtime.resumeReady.remove(id);
                }
            }
    }

    /**
     * Nhận ý định điều khiển từ socket hiện đang nắm quyền của user/room. Bỏ input khi sai runtime,
     * chưa RUNNING, user không thuộc roster hoặc sequence cũ/trùng; validate hướng rồi lưu input và
     * mốc nhận trong RAM. Tick sử dụng input mới nhất, tự xóa input quá cũ; client không được quyết
     * định vị trí, HP hay điểm.
     */
    public void input(
            long userId,
            String matchId,
            String socketId,
            long sequence,
            String direction,
            boolean fire) {
        GameRuntime runtime = runtimes.get(matchId);
        if (runtime == null || !sockets.controls(userId, runtime.roomId, socketId)) return;
        synchronized (runtime) {
            String id = Long.toString(userId);
            if (runtime.mode != RuntimeMode.RUNNING || !runtime.state.players.containsKey(id))
                return;
            if (sequence <= runtime.sequences.getOrDefault(id, -1L)) return;
            PlayerInput input = new PlayerInput(direction, fire);
            if (!input.valid()) throw of(ValidateCode.GAME_INVALID_INPUT);
            runtime.sequences.put(id, sequence);
            runtime.lastInputAt.put(id, System.currentTimeMillis());
            runtime.inputs.put(id, input);
        }
    }

    /**
     * Phối hợp rời phòng giữa DB, runtime và socket. Nếu đang RECOVERING thì hủy khôi phục, sau đó
     * RoomService.leave ghi bỏ cuộc/xóa membership trước khi đổi HP trong RAM. Đóng socket và phát
     * lại lobby sau thao tác DB; engine đánh giá kết thúc khi roster còn lại đã thay đổi.
     */
    public void leave(long userId, String code) {
        RoomResponse room = rooms.byCode(code);
        GameRuntime runtime = active(room.id());
        if (runtime != null && runtime.mode == RuntimeMode.RECOVERING)
            cancelRecovery(userId, runtime.state.matchId);
        rooms.leave(userId, code);
        if (runtime != null)
            synchronized (runtime) {
                GameEngine.forfeit(runtime.state, Long.toString(userId));
                GameEngine.evaluate(runtime.state);
            }
        sockets.closeUser(userId);
        publishRoom(code);
    }

    /**
     * Ghi xác nhận chơi tiếp của người còn sống, còn membership và có socket trong cửa sổ phục hồi.
     * Giữ monitor runtime để gom tập ready nhất quán; khi đủ mọi người còn sống thì gọi transaction
     * matches.resume. Chỉ sau DB commit mới chuyển runtime RUNNING, xóa input/mốc disconnect và bỏ
     * deadline phục hồi.
     */
    public void resumeReady(long userId, String matchId) {
        matches.requireParticipant(matchId, userId);
        GameRuntime runtime = requireRuntime(matchId);
        synchronized (runtime) {
            rooms.requireMember(rooms.byCode(runtime.roomCode), userId);
            if (runtime.mode != RuntimeMode.RECOVERING) throw of(ValidateCode.MATCH_NOT_RECOVERING);
            PlayerState player = runtime.state.players.get(Long.toString(userId));
            if (player == null || player.hp <= 0 || !sockets.connected(userId, runtime.roomId)) {
                throw of(ValidateCode.MATCH_CANNOT_RESUME);
            }
            runtime.resumeReady.add(player.id);
            boolean allReady =
                    runtime.state.players.values().stream()
                            .filter(p -> p.hp > 0)
                            .allMatch(
                                    p ->
                                            runtime.resumeReady.contains(p.id)
                                                    && sockets.connected(
                                                            Long.parseLong(p.id), runtime.roomId));
            if (allReady) {
                if (Instant.now().isAfter(runtime.recoveryDeadline))
                    throw of(ValidateCode.MATCH_RECOVERY_EXPIRED);
                matches.resume(matchId);
                runtime.mode = RuntimeMode.RUNNING;
                runtime.inputs.clear();
                runtime.disconnectedAt.clear();
                runtime.recoveryDeadline = null;
                runtime.lastSavedAt = System.currentTimeMillis();
            }
        }
        publishRoom(runtime.roomCode);
    }

    /**
     * Cho người tham gia còn membership hủy trận đang RECOVERING. Xác minh quyền/trạng thái rồi đưa
     * ABORTED vào hàng đợi lưu; kết quả hủy không cộng ranking.
     */
    public void cancelRecovery(long userId, String matchId) {
        matches.requireParticipant(matchId, userId);
        GameRuntime runtime = requireRuntime(matchId);
        synchronized (runtime) {
            rooms.requireMember(rooms.byCode(runtime.roomCode), userId);
            if (runtime.mode != RuntimeMode.RECOVERING) throw of(ValidateCode.MATCH_NOT_RECOVERING);
            queueFinish(runtime, "RECOVERY_CANCELLED");
        }
    }

    /**
     * Lấy runtime đang có trong RAM hoặc trả MATCH_NOT_ACTIVE. Dùng sau khi entry point đã kiểm tra
     * quyền tham gia; helper không tự nạp hay tạo runtime từ DB.
     */
    private GameRuntime requireRuntime(String matchId) {
        GameRuntime runtime = runtimes.get(matchId);
        if (runtime == null) throw of(ValidateCode.MATCH_NOT_ACTIVE);
        return runtime;
    }

    /**
     * Tra runtime hiện tại theo roomId qua hai map RAM roomMatches/runtimes. Một phòng chỉ sở hữu
     * một runtime; không phát sinh truy vấn DB trong thao tác tra này.
     */
    private GameRuntime active(long roomId) {
        String id = roomMatches.get(roomId);
        return id == null ? null : runtimes.get(id);
    }

    /**
     * Chạy mỗi 50ms để điều khiển các runtime, khóa riêng từng runtime khi đọc/đổi state.
     * RECOVERING quá hạn thì hủy; RUNNING xử lý presence, input quá cũ, grace/forfeit và gọi engine
     * một bước. Tất cả người sống offline thì đóng băng và queueRecovery; DB chậm quá ngưỡng thì
     * SAVING để chờ lưu. Tick chỉ đưa snapshot vào queue và phát frame theo nhịp; các lệnh
     * repository chạy ở executor persistence/service.
     */
    @Scheduled(fixedRate = 50)
    public void tick() {
        long now = System.currentTimeMillis();
        for (GameRuntime runtime : runtimes.values()) {
            try {
                synchronized (runtime) {
                    // Deadline phục hồi đã lưu bền; mở browser/restart không cấp thêm thời gian.
                    if (runtime.mode == RuntimeMode.RECOVERING) {
                        if (Instant.now().isAfter(runtime.recoveryDeadline))
                            queueFinish(runtime, "RECOVERY_EXPIRED");
                    } else if (runtime.mode == RuntimeMode.SAVING && runtime.pauseRequested) {
                        if (now >= runtime.nextSaveAttemptAt) queueRecovery(runtime);
                        continue;
                    } else if (runtime.mode == RuntimeMode.RUNNING) {
                        List<PlayerState> alive =
                                runtime.state.players.values().stream()
                                        .filter(p -> p.hp > 0)
                                        .toList();
                        boolean nobodyConnected =
                                !alive.isEmpty()
                                        && alive.stream()
                                                .noneMatch(
                                                        p ->
                                                                sockets.connected(
                                                                        Long.parseLong(p.id),
                                                                        runtime.roomId));
                        if (nobodyConnected && "RUNNING".equals(runtime.state.phase)) {
                            // Cả nhóm offline: đóng băng trước, ghi RECOVERING ở executor sau.
                            runtime.pauseRequested = true;
                            runtime.mode = RuntimeMode.SAVING;
                            runtime.inputs.clear();
                            queueRecovery(runtime);
                            continue;
                        }
                        // Chỉ áp grace/forfeit khi vẫn còn người khác online; không phạt cả nhóm.
                        for (PlayerState player : alive) {
                            if (!sockets.connected(Long.parseLong(player.id), runtime.roomId)) {
                                long since =
                                        runtime.disconnectedAt.computeIfAbsent(
                                                player.id, ignored -> now);
                                if (now - since >= reconnectMs)
                                    GameEngine.forfeit(runtime.state, player.id);
                            }
                        }
                        runtime.inputs
                                .entrySet()
                                .removeIf(
                                        e ->
                                                now
                                                                - runtime.lastInputAt.getOrDefault(
                                                                        e.getKey(), 0L)
                                                        > AppConstants.INPUT_STALE_MS);
                        if (now - runtime.lastSavedAt > Math.max(15000, checkpointMs * 5)) {
                            // Storage tụt quá xa: ngừng mô phỏng để giới hạn phần tiến trình chưa
                            // lưu.
                            runtime.mode = RuntimeMode.SAVING;
                            runtime.storageMessage =
                                    ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey();
                        } else GameEngine.step(runtime.state, runtime.inputs);
                    }
                    // Chỉ chụp RAM/xếp việc ghi; executor gọi service transaction để không chặn
                    // tick.
                    if (runtime.mode != RuntimeMode.RECOVERING
                            && !runtime.state.phase.equals("RUNNING")) queueFinish(runtime, null);
                    else if (runtime.mode != RuntimeMode.RECOVERING
                            && now - runtime.lastSavedAt >= checkpointMs
                            && now >= runtime.nextSaveAttemptAt) queueCheckpoint(runtime);
                    if (now - runtime.lastBroadcastAt >= 100) {
                        runtime.lastBroadcastAt = now;
                        sockets.broadcast(
                                runtime.roomId, new SocketEvent<>(SocketType.GAME, frame(runtime)));
                    }
                }
            } catch (RuntimeException exception) {
                log.error("Lỗi runtime matchId={}", runtime.state.matchId, exception);
            }
        }
    }

    /**
     * Tạo frame với bản sao sâu của state, mode và thông tin phục hồi. Caller giữ monitor runtime;
     * sao chép giúp tác vụ gửi JSON không đọc state đang bị tick sửa.
     */
    private GameFrameResponse frame(GameRuntime runtime) {
        return new GameFrameResponse(
                codec.copy(runtime.state),
                runtime.mode,
                List.copyOf(runtime.resumeReady),
                runtime.recoveryDeadline,
                runtime.storageMessage);
    }

    /**
     * Chụp state dưới khóa runtime rồi đưa việc checkpoint sang executor, tối đa một tác vụ cùng
     * loại/trận. Ghi nhận thời điểm chụp thay vì thời điểm hoàn tất I/O để phát hiện dữ liệu lưu
     * đang bị tụt sau mô phỏng. Thành công giải phóng SAVING nếu không có yêu cầu pause; lỗi đặt mã
     * thông báo/backoff và cho chu kỳ sau thử lại.
     */
    private void queueCheckpoint(GameRuntime runtime) {
        if (!runtime.checkpointQueued.compareAndSet(false, true)) return;
        GameState snapshot = codec.copy(runtime.state);
        long capturedAt = System.currentTimeMillis();
        submit(
                () -> {
                    try {
                        matches.checkpoint(snapshot);
                        runtime.lastSavedAt = capturedAt;
                        runtime.storageMessage = "";
                        synchronized (runtime) {
                            if (runtime.mode == RuntimeMode.SAVING && !runtime.pauseRequested)
                                runtime.mode = RuntimeMode.RUNNING;
                        }
                    } catch (RuntimeException exception) {
                        runtime.storageMessage =
                                ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey();
                        runtime.nextSaveAttemptAt = System.currentTimeMillis() + checkpointMs;
                        log.warn("Checkpoint thất bại matchId={}", snapshot.matchId, exception);
                    } finally {
                        runtime.checkpointQueued.set(false);
                    }
                },
                () -> runtime.checkpointQueued.set(false));
    }

    /**
     * Đưa thao tác pause vào executor khi cả nhóm mất kết nối; runtime dừng ở SAVING trong lúc chờ
     * DB. MatchService.pause lưu snapshot và deadline cùng transaction; sau commit mới chuyển RAM
     * RECOVERING và phát lobby. Dùng cùng cờ checkpointQueued để pause không chen lẫn với một
     * snapshot đang ghi; lỗi được thử lại ở tick sau.
     */
    private void queueRecovery(GameRuntime runtime) {
        if (!runtime.checkpointQueued.compareAndSet(false, true)) return;
        GameState snapshot = codec.copy(runtime.state);
        submit(
                () -> {
                    try {
                        Instant deadline = matches.pause(snapshot, recoveryMs);
                        synchronized (runtime) {
                            runtime.mode = RuntimeMode.RECOVERING;
                            runtime.pauseRequested = false;
                            runtime.recoveryDeadline = deadline;
                            runtime.resumeReady.clear();
                            runtime.storageMessage = "";
                            runtime.lastSavedAt = System.currentTimeMillis();
                        }
                        publishRoom(runtime.roomCode);
                    } catch (RuntimeException unavailable) {
                        runtime.nextSaveAttemptAt = System.currentTimeMillis() + checkpointMs;
                        runtime.storageMessage =
                                ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey();
                        log.warn("Chưa tạm lưu được matchId={}", snapshot.matchId, unavailable);
                    } finally {
                        runtime.checkpointQueued.set(false);
                    }
                },
                () -> runtime.checkpointQueued.set(false));
    }

    /**
     * Chụp kết quả và đưa FINISHED hoặc ABORTED sang executor, mỗi trận chỉ có một tác vụ finish
     * đang chờ. Giữ abortReason qua các lần retry để lỗi lưu không biến trận hủy thành trận
     * thắng/thua. Sau commit mới gỡ runtime, gỡ ánh xạ phòng và phát FINISHED; nếu DB lỗi thì giữ
     * runtime ở ENDING để thử lại.
     */
    private void queueFinish(GameRuntime runtime, String abortReason) {
        if (abortReason != null) {
            runtime.abortReason = abortReason;
        }
        if (System.currentTimeMillis() < runtime.nextSaveAttemptAt
                || !runtime.finishQueued.compareAndSet(false, true)) return;
        runtime.mode = RuntimeMode.ENDING;
        String finalAbortReason = runtime.abortReason;
        GameState snapshot = codec.copy(runtime.state);
        submit(
                () -> {
                    try {
                        if (finalAbortReason == null) matches.finish(snapshot);
                        else matches.abort(snapshot.matchId, finalAbortReason);
                        runtimes.remove(snapshot.matchId, runtime);
                        roomMatches.remove(runtime.roomId, snapshot.matchId);
                        sockets.broadcast(
                                runtime.roomId,
                                new SocketEvent<>(
                                        SocketType.FINISHED, Map.of("matchId", snapshot.matchId)));
                        publishRoom(runtime.roomCode);
                    } catch (RuntimeException exception) {
                        runtime.storageMessage =
                                ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey();
                        runtime.nextSaveAttemptAt = System.currentTimeMillis() + checkpointMs;
                        runtime.finishQueued.set(false);
                        log.error("Lưu kết quả thất bại matchId={}", snapshot.matchId, exception);
                    }
                },
                () -> runtime.finishQueued.set(false));
    }

    /**
     * Gửi tác vụ lưu vào executor có queue giới hạn. Khi queue đầy, gọi onRejected để trả lại cờ đã
     * giữ; chu kỳ sau có thể thử tiếp thay vì làm kẹt trận vĩnh viễn.
     */
    private void submit(Runnable task, Runnable onRejected) {
        try {
            persistence.execute(task);
        } catch (RejectedExecutionException overloaded) {
            onRejected.run();
        }
    }

    /**
     * Mỗi 5 giây đọc phòng WAITING theo lô và đối chiếu presence trong RAM. Thành viên offline quá
     * lobbyDisconnectMs được gom thành một tập rồi evict theo lô; không áp cơ chế này lên trận đang
     * chạy/khôi phục.
     */
    @Scheduled(fixedDelay = 5000)
    public void cleanLobbies() {
        long now = System.currentTimeMillis();
        Set<Long> expired = new HashSet<>();
        List<RoomResponse> waiting =
                rooms.list().stream().filter(r -> r.status().equals("WAITING")).toList();
        for (RoomResponse room : waiting)
            for (MemberResponse member : room.members()) {
                if (sockets.connected(member.userId(), room.id()))
                    lobbyMissing.remove(member.userId());
                else if (now - lobbyMissing.computeIfAbsent(member.userId(), ignored -> now)
                        >= lobbyDisconnectMs) expired.add(member.userId());
            }
        if (!expired.isEmpty()) {
            rooms.evictDisconnected(expired);
            expired.forEach(lobbyMissing::remove);
            // Phát lại theo snapshot đã lấy theo lô ở chu kỳ kế tiếp, client cũng có HTTP sync.
        }
    }

    /**
     * Chụp checkpoint cuối cho trận RUNNING/SAVING khi Spring đóng bean rồi chờ executor tối đa 8
     * giây. Đây là graceful shutdown; checkpoint định kỳ vẫn là điểm khôi phục nếu process bị kill
     * hoặc hết thời gian chờ.
     */
    @PreDestroy
    public void shutdown() throws InterruptedException {
        // Ghi checkpoint cuối nếu còn thời gian graceful shutdown; periodic save bảo vệ crash.
        for (GameRuntime runtime : runtimes.values())
            synchronized (runtime) {
                if (runtime.mode == RuntimeMode.RUNNING || runtime.mode == RuntimeMode.SAVING)
                    queueCheckpoint(runtime);
            }
        persistence.shutdown();
        persistence.awaitTermination(8, TimeUnit.SECONDS);
    }
}
