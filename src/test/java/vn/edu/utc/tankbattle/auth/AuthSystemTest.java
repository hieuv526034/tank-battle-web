package vn.edu.utc.tankbattle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.utc.tankbattle.auth.config.BootstrapAdminProperties;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.repository.BootstrapAdminRepository;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.BootstrapAdminInitializer;
import vn.edu.utc.tankbattle.auth.service.GoogleAccountService;
import vn.edu.utc.tankbattle.auth.support.AuthenticatedApiClient;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;
import vn.edu.utc.tankbattle.storage.support.TestDatabaseProperties;

/**
 * Chạy ứng dụng và hai SecurityFilterChain thật với H2 riêng. Không @Transactional trên test: cần
 * nhìn thấy commit thu hồi sau response lỗi replay.
 */
@SpringBootTest(properties = {"tank.auth.issuer=http://localhost:8080"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class AuthSystemTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        TestDatabaseProperties.configure(properties, "auth-system");
    }

    private static final String PASSWORD = "MatKhauHocTap!2026";

    @Autowired private MockMvc mvc;

    @Autowired private ObjectMapper json;

    @Autowired private JdbcTemplate db;

    @Autowired private JwtDecoder decoder;
    @Autowired private GoogleAccountService googleAccounts;
    @Autowired private AccountService accounts;
    @Autowired private BootstrapAdminRepository bootstrapRepository;
    @Autowired private PasswordEncoder passwords;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void bootstrapAdminCanLoginReceivesAdminJwtAndEntersAdminApi() throws Exception {
        boolean postgres =
                db.execute(
                        (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                                connection ->
                                        connection
                                                .getMetaData()
                                                .getDatabaseProductName()
                                                .equals("PostgreSQL"));
        if (postgres) {
            assertThat(db.queryForObject("SELECT current_schema()", String.class))
                    .isEqualTo("tank_battle");
            assertThat(
                            db.queryForObject(
                                    "SELECT COUNT(*) FROM pg_catalog.pg_tables WHERE schemaname='public' AND tablename IN ('users','flyway_schema_history','app_locks')",
                                    Integer.class))
                    .isZero();
        }
        MvcResult csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MvcResult login =
                mvc.perform(
                                post("/api/auth/login")
                                        .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                                        .header(
                                                "X-XSRF-TOKEN",
                                                body(csrf).at("/data/token").asText())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsBytes(
                                                        Map.of(
                                                                "username",
                                                                "admin",
                                                                "password",
                                                                "BootstrapTest!2026"))))
                        .andExpect(status().isOk())
                        .andReturn();
        String access = body(login).at("/data/accessToken").asText();
        assertThat(decoder.decode(access).getClaimAsStringList("roles")).containsExactly("ADMIN");
        mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void ordinaryUserCannotAccessAdminApiAndGetsCatalogError() throws Exception {
        AuthenticatedApiClient user = registerAndLogin();
        mvc.perform(
                        get("/api/admin/me")
                                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"))
                .andExpect(jsonPath("$.locale").value("en"));
    }

    @Test
    void simultaneousBootstrapCreatesExactlyOneAdmin() throws Exception {
        String username = "boot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> create =
                    () -> {
                        start.await();
                        bootstrap(username, "BootstrapFirst!2026");
                        return null;
                    };
            Future<Void> first = pool.submit(create);
            Future<Void> second = pool.submit(create);
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM users WHERE username=? AND role='ADMIN'",
                                Integer.class,
                                username))
                .isEqualTo(1);
    }

    @Test
    void repeatedBootstrapKeepsExistingPasswordAndProfile() {
        String username = "boot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        bootstrap(username, "BootstrapFirst!2026");
        String hash =
                db.queryForObject(
                        "SELECT password_hash FROM users WHERE username=?", String.class, username);
        db.update("UPDATE users SET display_name=? WHERE username=?", "Tên đã sửa", username);
        bootstrap(username, "BootstrapSecond!2026");
        assertThat(
                        db.queryForObject(
                                "SELECT password_hash FROM users WHERE username=?",
                                String.class,
                                username))
                .isEqualTo(hash);
        assertThat(
                        db.queryForObject(
                                "SELECT display_name FROM users WHERE username=?",
                                String.class,
                                username))
                .isEqualTo("Tên đã sửa");
        assertThat(passwords.matches("BootstrapFirst!2026", hash)).isTrue();
    }

    @Test
    void bootstrapNeverPromotesAnExistingRegularUser() {
        String username = "boot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accounts.create(username, "Người chơi", "fixture-hash");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> bootstrap(username, "BootstrapFirst!2026"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(accounts.credentials(username).getRole()).isEqualTo(AppConstants.UserRole.USER);
    }

    @Test
    void bootstrapRejectsWeakPasswordWithoutCreatingAccount() {
        String username = "boot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bootstrap(username, "admin"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM users WHERE username=?",
                                Integer.class,
                                username))
                .isZero();
    }

    /** Mô phỏng một lần startup qua transaction thật; mỗi lời gọi dùng cấu hình độc lập. */
    private void bootstrap(String username, String password) {
        BootstrapAdminInitializer initializer =
                new BootstrapAdminInitializer(
                        bootstrapRepository,
                        passwords,
                        new BootstrapAdminProperties(username, "Quản trị hệ thống", password));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> initializer.run(new DefaultApplicationArguments()));
    }

    @Test
    void googleIdentityIsStableAndDoesNotOverwriteLocalAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long localId = accounts.create("local_" + suffix, "Tên chung", "fixture-local-hash");
        String subject = "google-test-" + suffix;
        UserEntity first = googleAccounts.resolve(subject, "Tên chung");
        UserEntity again = googleAccounts.resolve(subject, "Tên khác");
        assertThat(first.getId()).isEqualTo(again.getId()).isNotEqualTo(localId);
        assertThat(first.getPasswordHash()).isNull();
        assertThat(again.getDisplayName()).isEqualTo("Tên chung");
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM external_identities WHERE provider='GOOGLE' AND subject=?",
                                Integer.class,
                                subject))
                .isEqualTo(1);
    }

    @Test
    void concurrentGoogleCallbacksResolveOneAccountAndRollbackTheLosingInsert() throws Exception {
        String subject = "google-race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Long> first =
                    pool.submit(
                            () -> {
                                start.await();
                                return googleAccounts.resolve(subject, "Google UTC").getId();
                            });
            Future<Long> second =
                    pool.submit(
                            () -> {
                                start.await();
                                return googleAccounts.resolve(subject, "Google UTC").getId();
                            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM external_identities WHERE provider='GOOGLE' AND subject=?",
                                Integer.class,
                                subject))
                .isEqualTo(1);
    }

    @Test
    void registersLogsInAndReturnsRealSignedJwtWithHttpOnlyRefresh() throws Exception {
        AuthenticatedApiClient client = registerAndLogin();
        Jwt claims = decoder.decode(client.accessToken());

        assertThat(claims.getClaimAsString("username")).isEqualTo(client.username());
        assertThat(claims.getAudience()).contains("tank-battle-api");
        assertThat(claims.getExpiresAt()).isAfter(claims.getIssuedAt());
        assertThat(client.refreshCookie().isHttpOnly()).isTrue();
        assertThat(client.refreshCookie().getPath()).isEqualTo("/api/auth");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYS_000"))
                .andExpect(jsonPath("$.data.username").value(client.username()));
    }

    @Test
    void refreshReplayCommitsRevocationAndRejectsTheNewAccessToken() throws Exception {
        AuthenticatedApiClient client = registerAndLogin();
        String sessionId = decoder.decode(client.accessToken()).getClaimAsString("sid");
        MvcResult rotated =
                mvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(client.csrfCookie(), client.refreshCookie())
                                        .header("X-XSRF-TOKEN", client.csrfToken()))
                        .andExpect(status().isOk())
                        .andReturn();
        String newAccess = body(rotated).at("/data/accessToken").asText();

        mvc.perform(
                        post("/api/auth/refresh")
                                .cookie(client.csrfCookie(), client.refreshCookie())
                                .header("X-XSRF-TOKEN", client.csrfToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
        Instant revokedAt =
                db.queryForObject(
                        "SELECT revoked_at FROM auth_sessions WHERE id=?",
                        (row, index) -> JdbcTimeUtils.readInstant(row, "revoked_at"),
                        sessionId);
        assertThat(revokedAt).isNotNull();
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void logoutRequiresCsrfThenImmediatelyRevokesTheSession() throws Exception {
        AuthenticatedApiClient client = registerAndLogin();
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        mvc.perform(
                        post("/api/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, bearer(client))
                                .cookie(client.csrfCookie())
                                .header("X-XSRF-TOKEN", client.csrfToken()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void missingOrInvalidBearerUsesCustomLocalizedErrors() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.locale").value("en"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void authenticationPostsRequireCsrfEvenWithBearer() throws Exception {
        AuthenticatedApiClient client = registerAndLogin();
        for (String endpoint :
                new String[] {"register", "login", "refresh", "logout", "logout-all"}) {
            mvc.perform(
                            post("/api/auth/" + endpoint)
                                    .header(HttpHeaders.AUTHORIZATION, bearer(client)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"));
        }
    }

    @Test
    void concurrentRefreshAllowsOneRotationThenRevokesTheReusedFamily() throws Exception {
        AuthenticatedApiClient client = registerAndLogin();
        String sessionId = decoder.decode(client.accessToken()).getClaimAsString("sid");
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcResult> refresh =
                () -> {
                    start.await();
                    return mvc.perform(
                                    post("/api/auth/refresh")
                                            .cookie(client.csrfCookie(), client.refreshCookie())
                                            .header("X-XSRF-TOKEN", client.csrfToken()))
                            .andReturn();
                };
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = executor.submit(refresh);
            Future<MvcResult> second = executor.submit(refresh);
            start.countDown();
            List<MvcResult> results =
                    List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 401);
            for (MvcResult result : results) {
                if (result.getResponse().getStatus() == 401) {
                    assertThat(body(result).path("code").asText()).isEqualTo("AUTH_007");
                }
            }
        }
        Instant revokedAt =
                db.queryForObject(
                        "SELECT revoked_at FROM auth_sessions WHERE id=?",
                        (row, index) -> JdbcTimeUtils.readInstant(row, "revoked_at"),
                        sessionId);
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void logoutAllRevokesBothLogins() throws Exception {
        AuthenticatedApiClient first = registerAndLogin();
        MvcResult secondLogin =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .cookie(first.csrfCookie())
                                        .header("X-XSRF-TOKEN", first.csrfToken())
                                        .content(
                                                json.writeValueAsBytes(
                                                        Map.of(
                                                                "username",
                                                                first.username(),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        String secondAccess = body(secondLogin).at("/data/accessToken").asText();

        mvc.perform(
                        post("/api/auth/logout-all")
                                .header(HttpHeaders.AUTHORIZATION, bearer(first))
                                .cookie(first.csrfCookie())
                                .header("X-XSRF-TOKEN", first.csrfToken()))
                .andExpect(status().isOk());

        for (String access : new String[] {first.accessToken(), secondAccess}) {
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_005"));
        }
    }

    @Test
    void openApiContainsAndRequirementForLogout() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode security = body(result).at("/paths/~1api~1auth~1logout/post/security");
        assertThat(security.isArray()).isTrue();
        assertThat(security.size()).isEqualTo(1);
        assertThat(security.get(0).has("bearerAuth")).isTrue();
        assertThat(security.get(0).has("csrfHeader")).isTrue();
    }

    private AuthenticatedApiClient registerAndLogin() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        String csrfToken = body(csrf).at("/data/token").asText();
        String username = "utc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfToken)
                                .content(
                                        json.writeValueAsBytes(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "displayName",
                                                        "Nguyễn An",
                                                        "password",
                                                        PASSWORD,
                                                        "confirmPassword",
                                                        PASSWORD))))
                .andExpect(status().isOk());

        MvcResult login =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .cookie(csrfCookie)
                                        .header("X-XSRF-TOKEN", csrfToken)
                                        .content(
                                                json.writeValueAsBytes(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie refresh = login.getResponse().getCookie(AppConstants.REFRESH_COOKIE_NAME);
        assertThat(refresh).isNotNull();
        return new AuthenticatedApiClient(
                username,
                csrfCookie,
                csrfToken,
                body(login).at("/data/accessToken").asText(),
                refresh);
    }

    private String bearer(AuthenticatedApiClient client) {
        return "Bearer " + client.accessToken();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
