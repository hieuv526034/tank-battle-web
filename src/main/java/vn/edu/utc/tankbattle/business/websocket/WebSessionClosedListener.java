package vn.edu.utc.tankbattle.business.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.common.event.WebSessionClosedEvent;

/** Nhận thông báo đóng phiên web và ngắt các socket gắn phiên ngay trong luồng logout. */
@Component
public class WebSessionClosedListener {
    private final SocketHub sockets;

    /** Nhận SocketHub để nối sự kiện logout session web với việc đóng socket liên quan. */
    public WebSessionClosedListener(SocketHub sockets) {
        this.sockets = sockets;
    }

    /**
     * Đóng các kết nối gắn với session web đang logout. Event dùng chung giúp SecurityConfig không
     * phụ thuộc trực tiếp package business.
     */
    @EventListener
    public void closeConnections(WebSessionClosedEvent event) {
        sockets.closeHttpSession(event.sessionId());
    }
}
