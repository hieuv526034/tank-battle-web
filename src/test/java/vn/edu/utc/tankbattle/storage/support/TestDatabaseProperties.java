package vn.edu.utc.tankbattle.storage.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Guard dùng chung cho mọi test tích hợp: chỉ nhận DB memory hoặc PostgreSQL loopback/CI. */
public final class TestDatabaseProperties {
    private TestDatabaseProperties() {}

    public static void configure(DynamicPropertyRegistry properties, String databaseName) {
        String url =
                System.getenv()
                        .getOrDefault(
                                "TEST_DATABASE_URL",
                                "jdbc:h2:mem:"
                                        + databaseName
                                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        if (!url.startsWith("jdbc:h2:mem:")
                && !url.matches(
                        "jdbc:postgresql://(localhost|127\\.0\\.0\\.1|postgres)(:[0-9]+)?/[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException(
                    "TEST_DATABASE_URL chỉ nhận DB test loopback/CI, không nhận production.");
        properties.add("spring.datasource.url", () -> url);
        properties.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "sa"));
        properties.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
        // Chạy integration PostgreSQL cùng schema production để phát hiện lệch JDBC/JPA/Flyway.
        // H2 memory giữ schema mặc định, không đụng file local của người phát triển.
        if (url.startsWith("jdbc:postgresql://")) {
            properties.add("spring.flyway.default-schema", () -> "tank_battle");
            properties.add("spring.flyway.schemas", () -> "tank_battle");
            properties.add("spring.flyway.create-schemas", () -> "true");
            properties.add("spring.jpa.properties.hibernate.default_schema", () -> "tank_battle");
            properties.add("spring.datasource.hikari.schema", () -> "tank_battle");
        }
    }
}
