package vn.edu.utc.tankbattle.business.model;

import jakarta.servlet.http.HttpSession;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/** Hàng gửi có giới hạn: client chậm không làm tăng bộ nhớ vô hạn hoặc chặn tick. */
public class PlayerConnection {
    public final long userId;
    public final long roomId;
    public final WebSocketSession socket;
    public final HttpSession httpSession;
    public final SocketIdentity identity;
    public final ArrayBlockingQueue<String> outgoing = new ArrayBlockingQueue<>(8);
    public final AtomicBoolean sending = new AtomicBoolean();

    public PlayerConnection(
            SocketIdentity identity,
            long roomId,
            WebSocketSession socket,
            HttpSession httpSession) {
        this.identity = identity;
        this.userId = identity.userId();
        this.roomId = roomId;
        this.socket = socket;
        this.httpSession = httpSession;
    }
}
