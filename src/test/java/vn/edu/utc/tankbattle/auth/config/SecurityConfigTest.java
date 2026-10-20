package vn.edu.utc.tankbattle.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.edu.utc.tankbattle.auth.filter.AuthRateLimitFilter;
import vn.edu.utc.tankbattle.auth.security.AuthErrorHandler;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.GoogleOidcUserService;

/**
 * Kiểm tra hai nhánh cấu hình OAuth bằng SecurityFilterChain thật, không gọi Google hoặc DB. Phải
 * giữ test thiếu registration vì local/Render chưa bật Google vẫn cần khởi động được.
 */
class SecurityConfigTest {
    private final AuthErrorHandler errors = mock(AuthErrorHandler.class);
    private final WebApplicationContextRunner context =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    SecurityAutoConfiguration.class,
                                    SecurityFilterAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class))
                    .withUserConfiguration(SecurityConfig.class)
                    .withBean(AccountService.class, () -> mock(AccountService.class))
                    .withBean(AuthRateLimitFilter.class, () -> mock(AuthRateLimitFilter.class))
                    .withBean(AuthErrorHandler.class, () -> errors)
                    .withBean(GoogleOidcUserService.class, () -> mock(GoogleOidcUserService.class))
                    .withBean(JwtDecoder.class, () -> mock(JwtDecoder.class))
                    .withBean(
                            BearerTokenFilterPostProcessor.class,
                            () -> new BearerTokenFilterPostProcessor(errors))
                    .withBean(ApiCsrfFilterPostProcessor.class, ApiCsrfFilterPostProcessor::new)
                    .withBean(
                            GoogleRedirectFilterPostProcessor.class,
                            () -> new GoogleRedirectFilterPostProcessor(errors));

    /** Local chưa cấu hình Google: đủ hai chain, form login hoạt động, không tạo filter OAuth. */
    @Test
    void startsWithoutGoogleRegistrationAndKeepsPasswordLogin() {
        context.run(
                application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).doesNotHaveBean(ClientRegistrationRepository.class);
                    assertThat(application).hasBean("apiSecurity").hasBean("webSecurity");
                    SecurityFilterChain web =
                            application.getBean("webSecurity", SecurityFilterChain.class);
                    assertThat(web.getFilters())
                            .anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance)
                            .noneMatch(OAuth2AuthorizationRequestRedirectFilter.class::isInstance)
                            .noneMatch(OAuth2LoginAuthenticationFilter.class::isInstance);
                });
    }

    /** Có registration hợp lệ: tạo cả filter redirect/callback; không cần kết nối Google. */
    @Test
    void enablesGoogleFiltersWhenRegistrationExists() {
        context.withBean(
                        ClientRegistrationRepository.class,
                        () ->
                                new InMemoryClientRegistrationRepository(
                                        CommonOAuth2Provider.GOOGLE
                                                .getBuilder("google")
                                                .clientId("test-client")
                                                .clientSecret("test-secret")
                                                .build()))
                .run(
                        application -> {
                            assertThat(application).hasNotFailed();
                            List<jakarta.servlet.Filter> filters =
                                    application
                                            .getBean("webSecurity", SecurityFilterChain.class)
                                            .getFilters();
                            assertThat(filters)
                                    .anyMatch(
                                            UsernamePasswordAuthenticationFilter.class::isInstance)
                                    .anyMatch(
                                            OAuth2AuthorizationRequestRedirectFilter.class
                                                    ::isInstance)
                                    .anyMatch(OAuth2LoginAuthenticationFilter.class::isInstance);
                        });
    }
}
