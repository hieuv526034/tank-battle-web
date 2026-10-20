package vn.edu.utc.tankbattle.business.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.edu.utc.tankbattle.business.websocket.GameWebSocketHandler;

/** Business dùng hợp đồng Spring; phần xác thực interceptor được cung cấp trong auth. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler handler;
    private final HandshakeInterceptor authentication;

    /**
     * Nhận handler nghiệp vụ và hợp đồng HandshakeInterceptor do auth cung cấp. Business phụ thuộc
     * interface Spring; thông tin danh tính đi qua model chung thay vì import class security của
     * auth.
     */
    public WebSocketConfig(GameWebSocketHandler handler, HandshakeInterceptor authentication) {
        this.handler = handler;
        this.authentication = authentication;
    }

    /**
     * Đăng ký endpoint /ws/game với interceptor xác thực trước khi handler nhận kết nối. Giữ kiểm
     * tra cùng origin mặc định; interceptor kiểm tra ticket/session rồi handler kiểm tra membership
     * và bind presence.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game").addInterceptors(authentication);
    }
}
