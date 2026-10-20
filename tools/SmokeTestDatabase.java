import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** Chỉ tạo/kiểm tra DB smoke trong cluster test loopback, không nhận URL production. */
public final class SmokeTestDatabase {
    private SmokeTestDatabase() {}

    /**
     * create dùng DB postgres để tạo tankbattle_smoke; verify kiểm tra migration trong DB mới.
     * Không dùng thông tin từ .env; cluster do test-postgres.ps1 tạo với user postgres/trust.
     */
    public static void main(String[] args) throws SQLException {
        if (args.length != 2 || !(args[0].equals("create") || args[0].equals("verify"))) {
            throw new IllegalArgumentException("Cần mode create/verify và URL DB test.");
        }
        String database = args[0].equals("create") ? "postgres" : "tankbattle_smoke";
        if (!args[1].matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/" + database)) {
            throw new IllegalArgumentException("Từ chối DB ngoài PostgreSQL smoke loopback.");
        }
        Properties properties = new Properties();
        properties.setProperty("user", "postgres");
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "5");
        try (Connection connection = DriverManager.getConnection(args[1], properties);
                Statement statement = connection.createStatement()) {
            if (args[0].equals("create")) {
                statement.execute("CREATE DATABASE tankbattle_smoke");
            } else {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM tank_battle.flyway_schema_history WHERE success AND version IS NOT NULL")) {
                    if (!rows.next() || rows.getInt(1) != 6) {
                        throw new IllegalStateException("DB smoke chưa áp dụng đủ V1-V6.");
                    }
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM tank_battle.users WHERE username='admin' AND role='ADMIN' AND enabled=TRUE AND password_hash IS NOT NULL")) {
                    if (!rows.next() || rows.getInt(1) != 1) {
                        throw new IllegalStateException("DB smoke chưa có tài khoản quản trị khởi tạo.");
                    }
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM pg_catalog.pg_tables WHERE schemaname='public'")) {
                    if (!rows.next() || rows.getInt(1) != 0) {
                        throw new IllegalStateException("Smoke phải tạo bảng trong tank_battle, không phải public.");
                    }
                }
            }
        }
    }
}
