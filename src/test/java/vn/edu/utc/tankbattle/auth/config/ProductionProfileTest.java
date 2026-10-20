package vn.edu.utc.tankbattle.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.DefaultResourceLoader;

/** Đọc cấu hình thật; kiểm binding pool mà không mở kết nối DB trong các kiểm tra profile. */
class ProductionProfileTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues(
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
}
