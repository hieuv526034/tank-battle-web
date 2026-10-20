package vn.edu.utc.tankbattle.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.utc.tankbattle.auth.config.BootstrapAdminProperties;
import vn.edu.utc.tankbattle.auth.repository.BootstrapAdminRepository;
import vn.edu.utc.tankbattle.auth.service.BootstrapAdminInitializer;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

/**
 * Hai pool đóng/mở lại cùng file, chạy migration và seed lần nữa. Test dùng thư mục tạm, không xóa
 * hoặc ghi vào .data thật.
 */
class H2FilePersistenceTest {
    @TempDir Path directory;

    @Test
    void keepsUsersSessionsAndCustomChangesAfterReopenWithoutDuplicatingSeeds() throws Exception {
        String databasePath =
                directory.resolve("tankbattle").toAbsolutePath().toString().replace('\\', '/');
        String url =
                "jdbc:h2:file:"
                        + databasePath
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;WRITE_DELAY=0";
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        long userId;

        try (HikariDataSource source = source(url)) {
            migrate(source);
            JdbcTemplate db = new JdbcTemplate(source);
            seed(db);
            userId = db.queryForObject("SELECT id FROM users WHERE username='admin'", Long.class);
            db.update(
                    "UPDATE users SET display_name=?,password_hash=? WHERE id=?",
                    "Tên đã thay đổi",
                    "custom-user-hash",
                    userId);
            db.update(
                    "INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,?)",
                    sessionId,
                    userId,
                    JdbcTimeUtils.toDatabase(expiry));
        }

        assertThat(Files.exists(Path.of(databasePath + ".mv.db"))).isTrue();

        try (HikariDataSource source = source(url)) {
            migrate(source);
            JdbcTemplate db = new JdbcTemplate(source);
            seed(db);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
            assertThat(
                            db.queryForObject(
                                    "SELECT COUNT(*) FROM app_locks WHERE name='CREATE_ROOM'",
                                    Integer.class))
                    .isEqualTo(1);
            assertThat(
                            db.queryForObject(
                                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL",
                                    Integer.class))
                    .isEqualTo(6);
            assertThat(
                            db.queryForObject(
                                    "SELECT display_name FROM users WHERE id=?",
                                    String.class,
                                    userId))
                    .isEqualTo("Tên đã thay đổi");
            assertThat(
                            db.queryForObject(
                                    "SELECT password_hash FROM users WHERE id=?",
                                    String.class,
                                    userId))
                    .isEqualTo("custom-user-hash");
            Instant storedExpiry =
                    db.queryForObject(
                            "SELECT expires_at FROM auth_sessions WHERE id=?",
                            (row, index) -> JdbcTimeUtils.readInstant(row, "expires_at"),
                            sessionId);
            assertThat(storedExpiry).isEqualTo(expiry);
        }
    }

    private HikariDataSource source(String url) {
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl(url);
        source.setUsername("sa");
        source.setPassword("");
        source.setMaximumPoolSize(2);
        source.setMinimumIdle(0);
        return source;
    }

    private void migrate(HikariDataSource source) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    private void seed(JdbcTemplate db) {
        BootstrapAdminInitializer initializer =
                new BootstrapAdminInitializer(
                        new BootstrapAdminRepository(db),
                        Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                        new BootstrapAdminProperties(
                                "admin", "Quản trị hệ thống", "BootstrapTest!2026"));
        new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()))
                .executeWithoutResult(status -> initializer.run(new DefaultApplicationArguments()));
    }
}
