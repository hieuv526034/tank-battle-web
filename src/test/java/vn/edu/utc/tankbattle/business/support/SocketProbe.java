package vn.edu.utc.tankbattle.business.support;

import com.fasterxml.jackson.databind.*;
import java.util.concurrent.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Đọc event từ WebSocket TCP thật với timeout hữu hạn. */
public class SocketProbe extends TextWebSocketHandler {
    private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
    private final ObjectMapper json = new ObjectMapper();
    public final CountDownLatch closed = new CountDownLatch(1);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        messages.offer(json.readTree(message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closed.countDown();
    }

    public JsonNode await(String type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            JsonNode message = messages.poll(200, TimeUnit.MILLISECONDS);
            if (message != null && type.equals(message.path("type").asText())) return message;
        }
        throw new AssertionError("Không nhận được WebSocket event " + type);
    }
}
