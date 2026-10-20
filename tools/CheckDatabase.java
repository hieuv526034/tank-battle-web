import java.sql.*;
import java.util.Properties;

/** Kiểm tra JDBC chỉ đọc. Thông tin truy cập lấy từ biến môi trường, không in password/URL. */
public class CheckDatabase {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("SPRING_DATASOURCE_USERNAME"));
        properties.setProperty("password", System.getenv("SPRING_DATASOURCE_PASSWORD"));
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "15");
        try (Connection connection = DriverManager.getConnection(System.getenv("SPRING_DATASOURCE_URL"), properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement query = connection.createStatement()) {
                query.setQueryTimeout(10);
                try (ResultSet row = query.executeQuery("SELECT current_database(), current_schema(), current_setting('server_version'), to_regnamespace('tank_battle') IS NOT NULL")) {
                    row.next();
                    System.out.println("CONNECTED database=" + row.getString(1) + " schema=" + row.getString(2) + " PostgreSQL=" + row.getString(3));
                    System.out.println("TARGET_SCHEMA_EXISTS=" + row.getBoolean(4));
                }
                try (ResultSet rows = query.executeQuery("SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='tank_battle' ORDER BY tablename")) {
                    int count = 0;
                    while (rows.next()) { System.out.println("TABLE " + rows.getString(1)); count++; }
                    System.out.println("TABLE_COUNT=" + count);
                }
            } finally { connection.rollback(); }
            System.out.println("READ_ONLY_CHECK_OK");
        } catch (SQLException failure) {
            // Không dùng getMessage(): message của driver có thể chứa thông tin kết nối.
            System.err.println("CONNECTION_FAILED SQLSTATE=" + failure.getSQLState() + " type=" + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
