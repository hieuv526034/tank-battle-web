package vn.edu.utc.tankbattle.business.websocket;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.utc.tankbattle.common.event.ApiSessionsRevokedEvent;

/** Chỉ đóng socket sau commit; rollback DB không làm mất kết nối đang hợp lệ. */
@Component
public class ApiSessionsRevokedListener {
    private final SocketHub sockets;

    /** Nhận SocketHub để tác động tới kết nối sau khi phần auth phát sự kiện chung. */
    public ApiSessionsRevokedListener(SocketHub sockets) {
        this.sockets = sockets;
    }

    /**
     * Nhận sự kiện sau khi transaction thu hồi phiên đã commit. Có sid thì đóng đúng phiên; có
     * userId thì đóng socket hiện tại của user. Nếu transaction rollback, listener không chạy.
     */
    @TransactionalEventListener
    public void revoked(ApiSessionsRevokedEvent event) {
        if (event.sessionId() != null) sockets.closeAuthSession(event.sessionId());
        else if (event.userId() != null) sockets.closeUser(event.userId());
    }
}
