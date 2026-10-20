package vn.edu.utc.tankbattle.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.RefreshCookieService;
import vn.edu.utc.tankbattle.auth.service.TokenService;
import vn.edu.utc.tankbattle.common.config.LocaleConfig;
import vn.edu.utc.tankbattle.common.exception.ApiExceptionHandler;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;
import vn.edu.utc.tankbattle.common.validation.RequestNormalizationAdvice;

/** Test tầng MVC/validation; test này không chứng minh filter chain, CSRF hay DB đã chạy đúng. */
class AuthApiErrorContractTest {
    private MockMvc mvc;
    private AuthenticationManager authentication;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ApiResponseFactory responses = new ApiResponseFactory(messages);
        validator = new LocaleConfig().validator(messages);
        validator.afterPropertiesSet();
        authentication = mock(AuthenticationManager.class);
        AuthApiController controller =
                new AuthApiController(
                        authentication,
                        mock(AccountService.class),
                        mock(PasswordEncoder.class),
                        mock(TokenService.class),
                        mock(RefreshCookieService.class),
                        responses);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new ApiExceptionHandler(responses),
                                new RequestNormalizationAdvice())
                        .setValidator(validator)
                        .setLocaleResolver(new LocaleConfig().localeResolver())
                        .build();
    }

    @AfterEach
    void cleanup() {
        validator.close();
    }

    @Test
    void incorrectPasswordHasCustomEnglishError() throws Exception {
        when(authentication.authenticate(any()))
                .thenThrow(new BadCredentialsException("hidden-password"));
        mvc.perform(
                        post("/api/auth/login")
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"username":"utc_student","password":"MatKhauHocTap!2026"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.messageKey").value("auth.invalid_credentials"))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "hidden-password"))));
    }

    @Test
    void authenticationServiceFailureHas503InsteadOfIncorrectPassword() throws Exception {
        when(authentication.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("private-db-info"));
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"username":"utc_student","password":"MatKhauHocTap!2026"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYS_006"))
                .andExpect(jsonPath("$.messageKey").value("system.service_unavailable"));
    }

    @Test
    void invalidRegistrationHasFieldCodesAndDoesNotEchoPassword() throws Exception {
        mvc.perform(
                        post("/api/auth/register")
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"username":"<script>","displayName":"Nguyễn An",
                                 "password":"secret","confirmPassword":"secret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"))
                .andExpect(jsonPath("$.errors.username.code").value("USER_002"))
                .andExpect(jsonPath("$.errors.password.code").value("USER_004"))
                .andExpect(jsonPath("$.locale").value("vi"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void passwordMismatchUsesFieldCatalogEntry() throws Exception {
        mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"username":"utc_student","displayName":"Nguyễn An",
                                 "password":"MatKhauHocTap!2026","confirmPassword":"OtherPassword!2026"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmPassword.code").value("USER_005"))
                .andExpect(
                        jsonPath("$.errors.confirmPassword.messageKey")
                                .value("user.password.mismatch"));
    }

    @Test
    void malformedJsonUsesCustomError() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"))
                .andExpect(jsonPath("$.messageKey").value("system.malformed_json"));
    }
}
