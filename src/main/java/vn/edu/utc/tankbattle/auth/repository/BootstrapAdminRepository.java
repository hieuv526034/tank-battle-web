package vn.edu.utc.tankbattle.auth.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.auth.model.BootstrapAdminAccount;

/** SQL bootstrap chạy trong transaction của initializer; không query/save trong vòng lặp. */
@Repository
public class BootstrapAdminRepository {
    private final JdbcTemplate db;

    /** Nhận JDBC dùng chung datasource/transaction với các repository auth khác. */
    public BootstrapAdminRepository(JdbcTemplate db) {
        this.db = db;
    }

    /** Khóa hàng guard do Flyway V6 tạo; tiến trình khác chờ commit trước khi kiểm tra username. */
    public void lockBootstrap() {
        db.queryForObject(
                "SELECT name FROM app_locks WHERE name='BOOTSTRAP_ADMIN' FOR UPDATE", String.class);
    }

    /** Đọc trạng thái tài khoản theo username đã chuẩn hóa, không mang hash ra service. */
    public Optional<BootstrapAdminAccount> find(String username) {
        return db
                .query(
                        "SELECT role,enabled,(password_hash IS NOT NULL) AS password_login FROM users WHERE username=?",
                        (row, index) ->
                                new BootstrapAdminAccount(
                                        row.getString("role"),
                                        row.getBoolean("enabled"),
                                        row.getBoolean("password_login")),
                        username)
                .stream()
                .findFirst();
    }

    /** Tạo một admin với hash, role và audit; unique username là lớp bảo vệ cuối cùng. */
    public void insert(String username, String displayName, String passwordHash) {
        db.update(
                "INSERT INTO users(username,display_name,password_hash,role,enabled,created_at,updated_at) VALUES(?,?,?,'ADMIN',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                username,
                displayName,
                passwordHash);
    }
}
