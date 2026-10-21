package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utc.tankbattle.auth.dto.response.WebSessionResponse;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Cấp CSRF phiên web riêng với CSRF cookie của API; không trả thông tin tài khoản. */
@RestController
@StandardApiErrors
public class WebSessionController {
    private final ObjectProvider<ClientRegistrationRepository> clients;
    private final ApiResponseFactory responses;

    /** Nhận cấu hình Google tùy chọn và factory phản hồi dùng chung. */
    public WebSessionController(
            ObjectProvider<ClientRegistrationRepository> clients, ApiResponseFactory responses) {
        this.clients = clients;
        this.responses = responses;
    }

    /** Không cho cache CSRF/session, kể cả khi vỏ trang được Service Worker lưu để chơi offline. */
    @GetMapping("/auth/session")
    @Operation(summary = "Trạng thái phiên web và CSRF dùng cho form đăng nhập")
    public ApiResponse<WebSessionResponse> session(
            Authentication authentication, CsrfToken csrf, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        boolean authenticated =
                authentication != null
                        && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken);
        return responses.success(
                new WebSessionResponse(
                        authenticated,
                        clients.getIfAvailable() != null,
                        csrf.getHeaderName(),
                        csrf.getToken()));
    }
}
