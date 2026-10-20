package vn.edu.utc.tankbattle.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import vn.edu.utc.tankbattle.auth.config.BearerTokenFilterPostProcessor;
import vn.edu.utc.tankbattle.auth.config.GoogleRedirectFilterPostProcessor;
import vn.edu.utc.tankbattle.auth.filter.AuthRateLimitFilter;
import vn.edu.utc.tankbattle.common.config.LocaleConfig;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Kiểm tra JSON thật do security ghi, bao gồm locale và chống lộ nội dung exception. */
class AuthErrorHandlerTest {
    private final ObjectMapper json = new ObjectMapper();
    private AuthErrorHandler handler;
    private SecurityErrorWriter writer;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        writer =
                new SecurityErrorWriter(
                        json,
                        new ApiResponseFactory(messages),
                        new LocaleConfig().localeResolver());
        handler = new AuthErrorHandler(writer);
    }

    static Stream<Arguments> loginFailures() {
        return Stream.of(
                Arguments.of(
                        new BadCredentialsException("secret-password"),
                        ValidateCode.AUTH_INVALID_CREDENTIALS),
                Arguments.of(
                        new InternalAuthenticationServiceException("jdbc-secret"),
                        ValidateCode.SYS_SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("loginFailures")
    void loginUsesCatalogInsteadOfExceptionMessage(
            AuthenticationException failure, ValidateCode expected) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.loginFailure(request("en"), response, failure);

        JsonNode body = assertError(response, expected, "en");
        assertThat(body.path("message").asText()).doesNotContain("secret", "jdbc");
        assertThat(response.getContentAsString()).doesNotContain(failure.getMessage());
    }

    @Test
    void missingLoginUsesVietnameseFallbackAndBearerChallenge() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.unauthenticated(request("fr"), response, new BadCredentialsException("hidden"));

        assertError(response, ValidateCode.AUTH_LOGIN_REQUIRED, "vi");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void jwtDoesNotExposeLibraryDescriptionInBodyOrHeader() throws Exception {
        MockHttpServletRequest request = request("vi-VN");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.unauthenticated(
                request, response, new InvalidBearerTokenException("private-key-detail"));

        assertError(response, ValidateCode.AUTH_INVALID_TOKEN, "vi");
        assertThat(response.getContentAsString()).doesNotContain("private-key-detail");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    void csrfAndPermissionHaveDifferentCatalogEntries() throws Exception {
        MockHttpServletResponse csrf = new MockHttpServletResponse();
        handler.denied(request("en"), csrf, new MissingCsrfTokenException("cookie-secret"));
        assertError(csrf, ValidateCode.AUTH_CSRF_INVALID, "en");

        MockHttpServletResponse permission = new MockHttpServletResponse();
        handler.denied(request("en"), permission, new AccessDeniedException("role-secret"));
        assertError(permission, ValidateCode.AUTH_ACCESS_DENIED, "en");
        assertThat(permission.getContentAsString()).doesNotContain("role-secret");
    }

    @Test
    void googleDoesNotEchoProviderError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.googleFailure(
                request("vi"),
                response,
                new OAuth2AuthenticationException(
                        new OAuth2Error("provider_internal_detail"), "google-secret"));
        assertError(response, ValidateCode.AUTH_OAUTH_FAILED, "vi");
        assertThat(response.getContentAsString())
                .doesNotContain("provider_internal_detail", "google-secret");
    }

    @Test
    void invalidGoogleRegistrationUsesCustomErrorAtRedirectStage() throws Exception {
        ClientRegistrationRepository clients = registrationId -> null;
        OAuth2AuthorizationRequestRedirectFilter filter =
                new OAuth2AuthorizationRequestRedirectFilter(clients);
        new GoogleRedirectFilterPostProcessor(handler).postProcess(filter);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorization/missing");
        request.setServletPath("/oauth2/authorization/missing");
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("Registration không tồn tại không được redirect.");
                });

        assertError(response, ValidateCode.AUTH_OAUTH_FAILED, "en");
    }

    @Test
    void rateLimitReturnsSameEnvelopeAndRetryHeader() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(writer);
        AtomicInteger accepted = new AtomicInteger();
        MockHttpServletResponse last = null;
        for (int attempt = 0; attempt < 21; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setServletPath("/api/auth/login");
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en");
            last = new MockHttpServletResponse();
            filter.doFilter(
                    request, last, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());
        }

        assertThat(accepted.get()).isEqualTo(20);
        assertError(last, ValidateCode.SYS_TOO_MANY_REQUESTS, "en");
        assertThat(last.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    void bearerFilterReturnsCustom503ForDatabaseFailureInsteadOfRethrowing() throws Exception {
        AuthenticationManager authentication =
                ignored -> {
                    throw new InternalAuthenticationServiceException(
                            "db-secret", new DataAccessResourceFailureException("jdbc-secret"));
                };
        BearerTokenAuthenticationFilter filter =
                new BearerTokenAuthenticationFilter(authentication);
        new BearerTokenFilterPostProcessor(handler).postProcess(filter);
        filter.setAuthenticationEntryPoint(handler::unauthenticated);
        MockHttpServletRequest request = request("en");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("Request lỗi không được đi tiếp.");
                });

        assertError(response, ValidateCode.SYS_SERVICE_UNAVAILABLE, "en");
        assertThat(response.getContentAsString()).doesNotContain("db-secret", "jdbc-secret");
    }

    private MockHttpServletRequest request(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setServletPath("/api/auth/me");
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, language);
        return request;
    }

    private JsonNode assertError(
            MockHttpServletResponse response, ValidateCode expected, String locale)
            throws Exception {
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(expected.getHttpStatus());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo(locale);
        assertThat(body.path("code").asText()).isEqualTo(expected.getCode());
        assertThat(body.path("messageKey").asText()).isEqualTo(expected.getMessageKey());
        assertThat(body.path("locale").asText()).isEqualTo(locale);
        assertThat(body.path("message").asText())
                .isNotBlank()
                .isNotEqualTo(expected.getMessageKey());
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("errors").size()).isZero();
        return body;
    }
}
