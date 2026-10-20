package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utc.tankbattle.auth.dto.response.SocketTicketResponse;
import vn.edu.utc.tankbattle.auth.service.SocketTicketService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Cấp vé ngắn hạn để kết nối WebSocket từ trình duyệt sau JWT login. */
@RestController
@Tag(name = "02. Vé WebSocket")
@StandardApiErrors
public class SocketTicketController {
    private final SocketTicketService tickets;
    private final ApiResponseFactory responses;

    public SocketTicketController(SocketTicketService tickets, ApiResponseFactory responses) {
        this.tickets = tickets;
        this.responses = responses;
    }

    @PostMapping("/api/auth/socket-ticket")
    @Operation(
            summary = "Cấp vé WebSocket dùng một lần",
            description =
                    "Cần Bearer và X-XSRF-TOKEN. Vé hết hạn tối đa 30 giây; socket hết hạn theo access token, cần refresh và xin vé mới để reconnect.",
            security = {
                @SecurityRequirement(name = "bearerAuth"),
                @SecurityRequirement(name = "csrfHeader")
            })
    public ApiResponse<SocketTicketResponse> issue(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return responses.success(tickets.issue(jwt));
    }
}
