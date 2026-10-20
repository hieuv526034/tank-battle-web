package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utc.tankbattle.auth.dto.request.LoginRequest;
import vn.edu.utc.tankbattle.auth.dto.request.RegisterRequest;
import vn.edu.utc.tankbattle.auth.dto.response.AuthTokenResponse;
import vn.edu.utc.tankbattle.auth.dto.response.CsrfResponse;
import vn.edu.utc.tankbattle.auth.dto.response.UserResponse;
import vn.edu.utc.tankbattle.auth.exception.AuthFailure;
import vn.edu.utc.tankbattle.auth.model.TokenGrant;
import vn.edu.utc.tankbattle.auth.security.AuthErrorMapper;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.RefreshCookieService;
import vn.edu.utc.tankbattle.auth.service.TokenService;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Controller chỉ điều phối request/response; toàn bộ cấp/rotate token ở TokenService. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01. Xác thực API", description = "Đăng ký, JWT, refresh rotation và thu hồi phiên.")
@StandardApiErrors
public class AuthApiController {
    private final AuthenticationManager authentication;
    private final AccountService users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final RefreshCookieService cookies;
    private final ApiResponseFactory responses;

    public AuthApiController(
            AuthenticationManager authentication,
            AccountService users,
            PasswordEncoder passwords,
            TokenService tokens,
            RefreshCookieService cookies,
            ApiResponseFactory responses) {
        this.authentication = authentication;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.cookies = cookies;
        this.responses = responses;
    }

    @GetMapping("/csrf")
    @Operation(
            summary = "Lấy CSRF cho API",
            description =
                    """
                Gọi trước login/register/refresh. Lưu cookie XSRF-TOKEN và gửi
                data.token trong X-XSRF-TOKEN. Không cần Bearer.
                """)
    public ApiResponse<CsrfResponse> csrf(CsrfToken token, HttpServletResponse response) {
        noStore(response);
        token.getToken();
        return responses.success(
                new CsrfResponse(
                        token.getHeaderName(), token.getParameterName(), token.getToken()));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Đăng ký tài khoản bằng mật khẩu",
            description =
                    """
                Username 4–24 chữ/số/gạch dưới, mật khẩu 12–64 ký tự,
                hai mật khẩu khớp. Thành công chưa tự đăng nhập. Cần header CSRF.
                """,
            security = @SecurityRequirement(name = "csrfHeader"))
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        request.normalize();
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.of(ValidateCode.USER_PASSWORD_MISMATCH);
        }
        try {
            users.create(
                    request.getUsername(),
                    request.getDisplayName(),
                    passwords.encode(request.getPassword()));
        } catch (DataIntegrityViolationException duplicate) {
            throw BusinessException.of(ValidateCode.USER_USERNAME_TAKEN);
        }
        return responses.success(users.byUsername(request.getUsername()));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Đăng nhập và cấp access JWT",
            description =
                    """
                Cần CSRF. Access token ở data; refresh ở Set-Cookie HttpOnly.
                Không lưu token vào localStorage. Sai thông tin trả 401;
                quá nhiều lần trả 429; lỗi dịch vụ xác thực trả 503.
                """,
            security = @SecurityRequirement(name = "csrfHeader"))
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        request.normalize();
        try {
            authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.getUsername(), request.getPassword()));
        } catch (AuthenticationException rejected) {
            throw new AuthFailure(AuthErrorMapper.loginFailure(rejected));
        }
        TokenGrant grant = tokens.issue(users.credentials(request.getUsername()));
        response.setHeader(HttpHeaders.SET_COOKIE, cookies.write(grant));
        noStore(response);
        return responses.success(grant.access());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Đổi refresh để lấy cặp token mới",
            description =
                    """
                Gửi CSRF và cookie TANK_REFRESH, không gửi Bearer hết hạn.
                Mỗi refresh chỉ dùng một lần. Dùng lại token cũ làm thu hồi toàn bộ
                phiên đó. Các tab cần phối hợp một lần refresh tại một thời điểm.
                """,
            security = @SecurityRequirement(name = "csrfHeader"))
    public ApiResponse<AuthTokenResponse> refresh(
            @CookieValue(name = AppConstants.REFRESH_COOKIE_NAME, required = false) String refresh,
            HttpServletResponse response) {
        noStore(response);
        try {
            TokenGrant grant = tokens.refresh(refresh);
            response.setHeader(HttpHeaders.SET_COOKIE, cookies.write(grant));
            return responses.success(grant.access());
        } catch (AuthFailure rejected) {
            response.setHeader(HttpHeaders.SET_COOKIE, cookies.clear());
            throw rejected;
        }
    }

    @GetMapping("/me")
    @Operation(
            summary = "Thông tin tài khoản của access JWT",
            description =
                    """
                Không dùng userId do client tự khai. Token bị thu hồi/hết hạn
                hoặc sai issuer/audience bị từ chối.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<UserResponse> me(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        noStore(response);
        return responses.success(users.byUsername(jwt.getClaimAsString("username")));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Thu hồi phiên JWT hiện tại",
            description =
                    """
                Cần cả Bearer hợp lệ và CSRF. Thu hồi sid trong DB, xóa cookie refresh.
                Token access của phiên này bị từ chối từ request kế tiếp.
                Session Thymeleaf là phiên riêng.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        tokens.revoke(jwt.getClaimAsString("sid"));
        response.setHeader(HttpHeaders.SET_COOKIE, cookies.clear());
        noStore(response);
        return responses.success(null);
    }

    @PostMapping("/logout-all")
    @Operation(
            summary = "Thu hồi mọi phiên JWT của tài khoản",
            description =
                    """
                Cần Bearer và CSRF. Một câu UPDATE thu hồi mọi auth_session của user;
                không gọi save theo từng token. Không tự hủy các HttpSession form/Google khác.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> logoutAll(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        tokens.revokeAll(Long.parseLong(jwt.getSubject()));
        response.setHeader(HttpHeaders.SET_COOKIE, cookies.clear());
        noStore(response);
        return responses.success(null);
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
