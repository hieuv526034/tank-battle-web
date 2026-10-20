package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utc.tankbattle.auth.dto.response.AuthTokenResponse;
import vn.edu.utc.tankbattle.auth.model.TokenGrant;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.RefreshCookieService;
import vn.edu.utc.tankbattle.auth.service.TokenService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/**
 * Sau form/Google login, client có thể đổi session thành token bằng POST có CSRF. Không nhét JWT
 * vào URL callback, HTML hay query string.
 */
@RestController
@Tag(name = "02. Đổi session", description = "Cầu nối từ form/Google sang API JWT.")
@StandardApiErrors
public class SessionTokenController {
    private final AccountService users;
    private final TokenService tokens;
    private final RefreshCookieService cookies;
    private final ApiResponseFactory responses;

    public SessionTokenController(
            AccountService users,
            TokenService tokens,
            RefreshCookieService cookies,
            ApiResponseFactory responses) {
        this.users = users;
        this.tokens = tokens;
        this.cookies = cookies;
        this.responses = responses;
    }

    @PostMapping("/auth/token")
    @Operation(
            summary = "Đổi session đã đăng nhập lấy JWT",
            description =
                    "Yêu cầu JSESSIONID của form/Google và CSRF session X-CSRF-TOKEN. Không dùng CSRF của /api/auth/csrf cho endpoint này. JWT không xuất hiện trong URL Google callback.",
            security = @SecurityRequirement(name = "webCsrfHeader"))
    public ApiResponse<AuthTokenResponse> exchange(
            Principal principal, HttpServletResponse response) {
        TokenGrant grant = tokens.issue(users.credentials(principal.getName()));
        response.setHeader(HttpHeaders.SET_COOKIE, cookies.write(grant));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return responses.success(grant.access());
    }
}
