package vn.edu.utc.tankbattle.auth.security;

import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.server.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.SocketTicketService;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/** Xác thực một lần tại handshake; business chỉ nhận SocketIdentity đã kiểm tra. */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {
    private final SocketTicketService tickets;
    private final AccountService users;
    private final SecurityErrorWriter errors;
    private final AcceptHeaderLocaleResolver locales;

    /**
     * Nhận service vé, tài khoản, writer lỗi và resolver locale dùng chung cho bước HTTP handshake.
     */
    public WebSocketAuthHandshakeInterceptor(
            SocketTicketService tickets,
            AccountService users,
            SecurityErrorWriter errors,
            AcceptHeaderLocaleResolver locales) {
        this.tickets = tickets;
        this.users = users;
        this.errors = errors;
        this.locales = locales;
    }

    /**
     * Kiểm tra Origin rồi xác thực ticket dùng một lần hoặc session web trước khi nâng HTTP thành
     * WebSocket. Đặt SocketIdentity/HttpSession/locale vào attributes cho handler dùng; quyền
     * membership được handler kiểm tra sau khi kết nối mở. Lỗi xác thực/quyền/DB ghi cùng envelope
     * custom và từ chối handshake, không để exception text đi tới client.
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes)
            throws Exception {
        if (!(request instanceof ServletServerHttpRequest servlet)
                || !(response instanceof ServletServerHttpResponse servletResponse)) return false;
        try {
            String origin = request.getHeaders().getOrigin();
            if (origin != null && !sameOrigin(URI.create(origin), request.getURI())) {
                throw BusinessException.of(ValidateCode.AUTH_ACCESS_DENIED);
            }
            String ticket =
                    UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams()
                            .getFirst("ticket");
            SocketIdentity identity;
            if (ticket != null) {
                identity = tickets.consume(ticket);
            } else if (request.getPrincipal() instanceof Authentication authentication
                    && authentication.isAuthenticated()) {
                UserEntity user = users.credentials(authentication.getName());
                HttpSession session = servlet.getServletRequest().getSession(false);
                if (session == null || !user.isEnabled())
                    throw BusinessException.of(ValidateCode.AUTH_LOGIN_REQUIRED);
                identity =
                        new SocketIdentity(
                                user.getId(),
                                user.getUsername(),
                                null,
                                Instant.now().plusSeconds(session.getMaxInactiveInterval()));
                attributes.put(AppConstants.SOCKET_HTTP_SESSION, session);
            } else {
                throw BusinessException.of(ValidateCode.AUTH_LOGIN_REQUIRED);
            }
            attributes.put(AppConstants.SOCKET_IDENTITY, identity);
            // Cùng resolver với MVC/security: thiếu header phải dùng vi, không theo locale máy chủ.
            attributes.put(
                    AppConstants.SOCKET_LOCALE, locales.resolveLocale(servlet.getServletRequest()));
            return true;
        } catch (BusinessException rejected) {
            errors.write(
                    servlet.getServletRequest(),
                    servletResponse.getServletResponse(),
                    rejected.getValidateCode());
            return false;
        } catch (IllegalArgumentException malformed) {
            errors.write(
                    servlet.getServletRequest(),
                    servletResponse.getServletResponse(),
                    ValidateCode.AUTH_INVALID_TOKEN);
            return false;
        } catch (org.springframework.dao.DataAccessException unavailable) {
            errors.write(
                    servlet.getServletRequest(),
                    servletResponse.getServletResponse(),
                    ValidateCode.SYS_SERVICE_UNAVAILABLE);
            return false;
        }
    }

    /**
     * So sánh scheme/host/port của Origin với URL server sau khi chuẩn hóa cổng mặc định
     * HTTP/HTTPS. Ngăn trang bên ngoài lợi dụng cookie phiên web để mở socket vào ứng dụng.
     */
    private boolean sameOrigin(URI origin, URI target) {
        int originPort =
                origin.getPort() == -1
                        ? ("https".equals(origin.getScheme()) ? 443 : 80)
                        : origin.getPort();
        int targetPort =
                target.getPort() == -1
                        ? ("https".equals(target.getScheme()) ? 443 : 80)
                        : target.getPort();
        return origin.getHost() != null
                && origin.getHost().equalsIgnoreCase(target.getHost())
                && origin.getScheme().equalsIgnoreCase(target.getScheme())
                && originPort == targetPort;
    }

    /**
     * Hook kết thúc handshake của Spring; hiện không giữ tài nguyên tạm cần dọn ở giai đoạn này.
     * Vòng đời kết nối thật do GameWebSocketHandler và SocketHub quản lý.
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {}
}
