package vn.edu.utc.tankbattle.business.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.utc.tankbattle.business.model.PlayerConnection;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/** I/O WebSocket chạy ở pool riêng, vòng game chỉ đưa message vào queue có giới hạn. */
@Component
public class SocketHub {
    private final ObjectMapper json;
    private final ConcurrentHashMap<Long, PlayerConnection> connections = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor writers =
            new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));

    /**
     * Nhận ObjectMapper và khởi pool gửi I/O tách khỏi tick game. Mỗi PlayerConnection giữ queue
     * riêng, còn map connections chỉ giữ socket mới nhất theo userId.
     */
    public SocketHub(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Gắn socket đã xác thực với user/phòng và thay thế kết nối trước nếu có. Đưa kết nối mới vào
     * map trước khi đóng cũ để callback cũ không xóa nhầm presence của socket mới.
     */
    public void bind(
            SocketIdentity identity, long roomId, WebSocketSession socket, HttpSession session) {
        PlayerConnection previous =
                connections.put(
                        identity.userId(), new PlayerConnection(identity, roomId, socket, session));
        // Đặt kết nối mới trước khi đóng cũ; callback của socket cũ không xóa socket mới.
        if (previous != null && !previous.socket.getId().equals(socket.getId())) {
            close(previous.socket);
        }
    }

    /**
     * Xóa presence chỉ khi socketId đang đóng vẫn trùng kết nối hiện tại của user. Trả true để
     * handler biết cần báo disconnected; false khi đây là callback của socket đã bị thay thế.
     */
    public boolean remove(long userId, String socketId) {
        PlayerConnection current = connections.get(userId);
        return current != null
                && current.socket.getId().equals(socketId)
                && connections.remove(userId, current);
    }

    /**
     * Kiểm tra quyền gửi input thuộc đúng user, room và socket mới nhất còn hiệu lực. Đây là đối
     * chiếu RAM được Coordinator gọi trước khi chấp nhận sequence.
     */
    public boolean controls(long userId, long roomId, String socketId) {
        PlayerConnection current = connections.get(userId);
        return current != null
                && current.roomId == roomId
                && current.socket.getId().equals(socketId)
                && valid(current);
    }

    /**
     * Kiểm tra user có socket hợp lệ tại phòng để start/resume hoặc xử lý grace mất kết nối. Không
     * query DB trên đường tick.
     */
    public boolean connected(long userId, long roomId) {
        PlayerConnection current = connections.get(userId);
        return current != null && current.roomId == roomId && valid(current);
    }

    /**
     * Kiểm tra hạn identity, tình trạng HttpSession nếu dùng web login và socket còn mở. Đóng
     * socket khi access/session hết hiệu lực; trạng thái sid bị thu hồi được listener đóng sau
     * commit, không polling DB mỗi tick.
     */
    private boolean valid(PlayerConnection connection) {
        try {
            if (!connection.identity.expiresAt().isAfter(Instant.now())) {
                close(connection.socket);
                return false;
            }
            if (connection.httpSession != null) connection.httpSession.getCreationTime();
            return connection.socket.isOpen();
        } catch (IllegalStateException expired) {
            close(connection.socket);
            return false;
        }
    }

    /** Lấy danh sách user đang kết nối hợp lệ trong một room, sắp xếp ổn định để trả lobby. */
    public List<Long> users(long roomId) {
        return connections.values().stream()
                .filter(c -> c.roomId == roomId && valid(c))
                .map(c -> c.userId)
                .sorted()
                .toList();
    }

    /**
     * Serialize event rồi đưa vào queue của một user nếu kết nối còn hợp lệ. Hàm chỉ xếp hàng;
     * worker thực hiện sendMessage nên caller không bị chặn bởi đường truyền của client.
     */
    public void send(long userId, Object event) {
        PlayerConnection connection = connections.get(userId);
        if (connection == null || !valid(connection)) return;
        enqueue(connection, serialize(event));
    }

    /**
     * Serialize event một lần rồi dùng chung JSON để xếp hàng cho các socket hợp lệ trong room.
     * Duyệt map RAM, tránh serialize/query lại theo từng người.
     */
    public void broadcast(long roomId, Object event) {
        String payload = serialize(event);
        // Chỉ duyệt kết nối trong RAM; không gọi repository theo từng người.
        connections.values().stream()
                .filter(c -> c.roomId == roomId && valid(c))
                .forEach(c -> enqueue(c, payload));
    }

    /**
     * Chuyển event DTO sang JSON bằng mapper dùng chung. Lỗi serialize là lỗi lập trình/dữ liệu nội
     * bộ, để caller xử lý/log; không gửi exception text cho client.
     */
    private String serialize(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Không mã hóa được message WebSocket", exception);
        }
    }

    /**
     * Đẩy message vào queue giới hạn của kết nối; khi đầy bỏ message cũ nhất để ưu tiên trạng thái
     * mới. Cờ sending CAS chỉ cho một worker drain một socket tại một thời điểm. Pool quá tải thì
     * đóng kết nối để client có thể reconnect/sync.
     */
    private void enqueue(PlayerConnection connection, String message) {
        if (!connection.outgoing.offer(message)) {
            connection.outgoing.poll();
            connection.outgoing.offer(message);
        }
        if (!connection.sending.compareAndSet(false, true)) return;
        try {
            writers.execute(() -> drain(connection));
        } catch (java.util.concurrent.RejectedExecutionException overloaded) {
            connection.sending.set(false);
            close(connection.socket);
        }
    }

    /**
     * Gửi tuần tự các message của một socket trên worker I/O. Kết thúc phải hạ cờ sending và kiểm
     * tra queue lần nữa để message đến sát lúc worker dừng không bị bỏ quên. Lỗi I/O đóng socket;
     * handler cập nhật presence từ callback.
     */
    private void drain(PlayerConnection connection) {
        try {
            String message;
            while ((message = connection.outgoing.poll()) != null && valid(connection)) {
                connection.socket.sendMessage(new TextMessage(message));
            }
        } catch (IOException exception) {
            close(connection.socket);
        } finally {
            connection.sending.set(false);
            // Xử lý race: message có thể đến giữa poll cuối và lúc bỏ cờ sending.
            String pending = connection.outgoing.poll();
            if (pending != null) enqueue(connection, pending);
        }
    }

    /** Đóng socket hiện tại của user, dùng cho rời phòng hoặc thu hồi tất cả phiên API. */
    public void closeUser(long userId) {
        PlayerConnection connection = connections.get(userId);
        if (connection != null) close(connection.socket);
    }

    /**
     * Đóng các socket gắn với HttpSession vừa logout. Chỉ xét kết nối có session web; kết nối bằng
     * ticket JWT được theo dõi theo authSessionId.
     */
    public void closeHttpSession(String sessionId) {
        connections.values().stream()
                .filter(c -> c.httpSession != null && c.httpSession.getId().equals(sessionId))
                .forEach(c -> close(c.socket));
    }

    /**
     * Đóng socket gắn với sid API đã bị revoke sau commit. Token của phiên khác vẫn có vòng đời
     * riêng, trừ khi caller thu hồi toàn user.
     */
    public void closeAuthSession(String sessionId) {
        connections.values().stream()
                .filter(c -> sessionId.equals(c.identity.authSessionId()))
                .forEach(c -> close(c.socket));
    }

    /**
     * Yêu cầu đóng WebSocket và để callback afterConnectionClosed xử lý việc xóa presence. Lỗi khi
     * đóng một socket đã hỏng được bỏ qua để tác vụ thu hồi/shutdown tiếp tục với các kết nối khác.
     */
    private void close(WebSocketSession socket) {
        try {
            socket.close(CloseStatus.NORMAL);
        } catch (IOException ignored) {
            // Callback đóng kết nối chịu trách nhiệm cập nhật presence.
        }
    }

    /**
     * Khi Spring đóng bean, đóng các socket và dừng pool writer để giải phóng tài nguyên của ứng
     * dụng.
     */
    @PreDestroy
    public void shutdown() {
        connections.values().forEach(c -> close(c.socket));
        writers.shutdownNow();
    }
}
