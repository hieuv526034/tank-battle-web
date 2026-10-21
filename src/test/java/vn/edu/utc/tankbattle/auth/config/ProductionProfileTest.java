package vn.edu.utc.tankbattle.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

/** Đọc cấu hình thật; kiểm binding pool mà không mở kết nối DB trong các kiểm tra profile. */
class ProductionProfileTest {
    @TempDir private Path temporaryDirectory;

    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues(
                            "APP_ENV_FILE=build/verification/does-not-exist-for-profile-test.env",
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:1/profile_check",
                            "SPRING_DATASOURCE_USERNAME=profile_test",
                            "SPRING_DATASOURCE_PASSWORD=profile_test_only");

    /** Pool nhận schema rõ ràng dù URL của Dashboard bỏ quên tham số currentSchema. */
    @Test
    void jdbcPoolUsesApplicationSchemaWithoutRelyingOnUrlParameters() {
        context.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            HikariDataSource datasource =
                                    application.getBean(HikariDataSource.class);
                            assertThat(datasource.getJdbcUrl())
                                    .isEqualTo("jdbc:postgresql://127.0.0.1:1/profile_check");
                            assertThat(datasource.getSchema()).isEqualTo("tank_battle");
                            assertThat(datasource.getMaximumPoolSize()).isEqualTo(5);
                            assertThat(datasource.getHikariPoolMXBean()).isNull();
                        });
    }

    /** Mặc định dùng prod/PostgreSQL và vẫn cấm khóa tạm nếu người vận hành bật nhầm cờ. */
    @Test
    void defaultsToProductionAndRequiresStableJwtKeys() {
        context.withPropertyValues("tank.auth.ephemeral-keys=true")
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            ConfigurableEnvironment environment = application.getEnvironment();
                            assertThat(environment.getActiveProfiles()).isEmpty();
                            assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isTrue();
                            assertThat(environment.getProperty("spring.datasource.url"))
                                    .startsWith("jdbc:postgresql:");
                            assertThat(environment.getProperty("spring.flyway.default-schema"))
                                    .isEqualTo("tank_battle");
                            assertThat(
                                            environment.getProperty(
                                                    "server.servlet.session.cookie.secure"))
                                    .isEqualTo("true");
                            AuthProperties properties =
                                    Binder.get(environment)
                                            .bind("tank.auth", AuthProperties.class)
                                            .get();
                            assertThatThrownBy(
                                            () ->
                                                    new JwtConfiguration()
                                                            .signingKey(
                                                                    properties,
                                                                    new DefaultResourceLoader(),
                                                                    environment))
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("JWT_PRIVATE_KEY");
                        });
    }

    /** Profile test thay thế mặc định prod và sử dụng H2/khóa tạm riêng, không nạp Neon. */
    @Test
    void testsOverrideProductionWithoutInheritingItsDatasource() {
        context.withPropertyValues("spring.profiles.active=test")
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            ConfigurableEnvironment environment = application.getEnvironment();
                            assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isFalse();
                            assertThat(environment.getProperty("spring.datasource.url"))
                                    .startsWith("jdbc:h2:mem:");
                            assertThat(environment.getProperty("spring.flyway.default-schema"))
                                    .isNull();
                            assertThat(environment.getProperty("tank.auth.ephemeral-keys"))
                                    .isEqualTo("true");
                        });
    }

    /**
     * Chọn prod rõ ràng như IDE; nạp file thật mà không tạo datasource/Flyway hoặc kết nối mạng.
     */
    @Test
    void localEnvResolvesDatabaseFlywayJwtAndHttpCookies() throws IOException {
        Path fixture = localEnvFixture();
        envContext(fixture)
                .withPropertyValues("spring.profiles.active=prod")
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            ConfigurableEnvironment environment = application.getEnvironment();
                            assertThat(environment.getActiveProfiles()).containsExactly("prod");
                            assertThat(environment.getProperty("spring.datasource.url"))
                                    .isEqualTo("jdbc:postgresql://127.0.0.1:1/env_fixture");
                            assertThat(environment.getProperty("spring.flyway.url"))
                                    .isEqualTo(environment.getProperty("spring.datasource.url"));
                            AuthProperties auth =
                                    Binder.get(environment)
                                            .bind("tank.auth", AuthProperties.class)
                                            .get();
                            assertThat(auth.privateKey())
                                    .isEqualTo("file:./secrets/jwt-private.pem");
                            assertThat(auth.publicKey()).isEqualTo("file:./secrets/jwt-public.pem");
                            assertThat(auth.issuer()).isEqualTo("http://localhost:8080");
                            assertThat(auth.secureCookie()).isFalse();
                            assertThat(
                                            environment.getProperty(
                                                    "server.servlet.session.cookie.secure"))
                                    .isEqualTo("false");
                        });
    }

    /** Biến môi trường thật của hosting vẫn thắng file local khi cùng khóa cấu hình. */
    @Test
    void hostingEnvironmentOverridesLocalFileAndKeepsHttpsCookies() throws IOException {
        envContext(localEnvFixture())
                .withInitializer(
                        application ->
                                application
                                        .getEnvironment()
                                        .getPropertySources()
                                        .addFirst(
                                                new SystemEnvironmentPropertySource(
                                                        "hostingFixture",
                                                        Map.of(
                                                                "SPRING_DATASOURCE_URL",
                                                                        "jdbc:postgresql://127.0.0.1:2/hosting_fixture",
                                                                "AUTH_SECURE_COOKIE", "true",
                                                                "SESSION_SECURE_COOKIE", "true"))))
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            ConfigurableEnvironment environment = application.getEnvironment();
                            assertThat(environment.getProperty("spring.datasource.url"))
                                    .isEqualTo("jdbc:postgresql://127.0.0.1:2/hosting_fixture");
                            assertThat(environment.getProperty("spring.flyway.url"))
                                    .isEqualTo("jdbc:postgresql://127.0.0.1:2/hosting_fixture");
                            assertThat(environment.getProperty("tank.auth.secure-cookie"))
                                    .isEqualTo("true");
                            assertThat(
                                            environment.getProperty(
                                                    "server.servlet.session.cookie.secure"))
                                    .isEqualTo("true");
                        });
    }

    /** Test profile không import .env dù file tồn tại và có cấu hình prod khác. */
    @Test
    void testProfileDoesNotReadLocalEnvFile() throws IOException {
        envContext(localEnvFixture())
                .withPropertyValues("spring.profiles.active=test")
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            assertThat(
                                            application
                                                    .getEnvironment()
                                                    .getProperty("spring.datasource.url"))
                                    .startsWith("jdbc:h2:mem:");
                            assertThat(
                                            application
                                                    .getEnvironment()
                                                    .getProperty("SPRING_DATASOURCE_URL"))
                                    .isNull();
                        });
    }

    /** Fixture chỉ chứa host loopback không lắng nghe; không sao chép .env của người dùng. */
    private Path localEnvFixture() throws IOException {
        Path fixture = temporaryDirectory.resolve("cau hinh test.env");
        Files.writeString(
                fixture,
                """
                SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:1/env_fixture
                SPRING_DATASOURCE_USERNAME=fixture_user
                SPRING_DATASOURCE_PASSWORD=fixture_password
                AUTH_ISSUER=http://localhost:8080
                JWT_PRIVATE_KEY=file:./secrets/jwt-private.pem
                JWT_PUBLIC_KEY=file:./secrets/jwt-public.pem
                AUTH_SECURE_COOKIE=false
                SESSION_SECURE_COOKIE=false
                """);
        return fixture;
    }

    /** ConfigData đọc application-prod.yml thật và import file chỉ định trước bước tạo bean. */
    private ApplicationContextRunner envContext(Path fixture) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "APP_ENV_FILE=" + fixture.toAbsolutePath().toString().replace('\\', '/'));
    }
}
