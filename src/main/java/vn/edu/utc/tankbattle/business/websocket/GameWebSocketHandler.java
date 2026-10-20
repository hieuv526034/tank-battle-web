package vn.edu.utc.tankbattle.business.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vn.edu.utc.tankbattle.business.dto.request.SocketRequest;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.dto.response.SocketEvent;
import vn.edu.utc.tankbattle.business.model.SocketRateWindow;
import vn.edu.utc.tankbattle.business.runtime.GameCoordinator;
import vn.edu.utc.tankbattle.business.service.RoomService;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.SocketType;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/**
 * Danh tính lấy từ handshake. Một user điều khiển qua một socket mới nhất, không tin userId từ
 * JSON.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final ObjectMapper json;
    private final Validator validator;
    private final RoomService rooms;
    private final GameCoordinator games;
    private final SocketHub sockets;
    private final ApiResponseFactory responses;
    private final Map<String, SocketRateWindow> windows = new ConcurrentHashMap<>();

    /**
     * Nhận bộ JSON/validator, service phòng, coordinator, socket hub và factory lỗi dùng chung.
     * Handler chỉ xác thực message/điều phối; engine tính state và các service giữ transaction.
     */
    public GameWebSocketHandler(
            ObjectMapper json,
            Validator validator,
            RoomService rooms,
            GameCoordinator games,
            SocketHub sockets,
            ApiResponseFactory responses) {
        this.json = json;
        this.validator = validator;
        this.rooms = rooms;
        this.games = games;
        this.sockets = sockets;
        this.responses = responses;
    }

    /**
     * Xử lý sau handshake thành công: lấy SocketIdentity, kiểm tra user đang ở phòng, đặt giới hạn
     * message/rate rồi bind socket. Sau khi bind mới báo connected cho Coordinator để phát
     * frame/lobby và reset input cũ.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SocketIdentity identity = identity(session);
        // Phải ở phòng trước khi mở socket; join bằng API đã được bảo vệ transaction.
        RoomResponse room = rooms.current(identity.userId()).orElse(null);
        if (room == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(AppConstants.MAX_SOCKET_MESSAGE_BYTES);
        session.getAttributes().put("roomId", room.id());
        windows.put(session.getId(), new SocketRateWindow());
        sockets.bind(
                identity,
                room.id(),
                session,
                (HttpSession) session.getAttributes().get(AppConstants.SOCKET_HTTP_SESSION));
        games.connected(identity.userId(), room.code());
    }

    /**
     * Đọc message từ socket mới nhất có quyền tại room, kiểm tra rate và Bean Validation trước khi
     * dispatch. input chuyển ý định/sequence sang RAM; sync trả frame có kiểm tra quyền; subscribe
     * phát lại lobby hiện tại. JSON sai/lỗi nghiệp vụ trả event ERROR có mã và locale; lỗi dịch vụ
     * được map SYS_SERVICE_UNAVAILABLE.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        SocketIdentity identity = identity(session);
        long roomId = (Long) session.getAttributes().get("roomId");
        if (!sockets.controls(identity.userId(), roomId, session.getId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        SocketRateWindow window = windows.get(session.getId());
        if (window == null || !window.allow()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            SocketRequest request = json.readValue(message.getPayload(), SocketRequest.class);
            if (request == null || !validator.validate(request).isEmpty())
                throw BusinessException.of(ValidateCode.GAME_INVALID_INPUT);
            switch (request.type) {
                case "input" -> {
                    if (request.matchId == null || request.direction == null)
                        throw BusinessException.of(ValidateCode.GAME_INVALID_INPUT);
                    games.input(
                            identity.userId(),
                            request.matchId,
                            session.getId(),
                            request.sequence,
                            request.direction,
                            request.fire);
                }
                case "sync" -> {
                    if (request.matchId == null)
                        throw BusinessException.of(ValidateCode.GAME_INVALID_INPUT);
                    sockets.send(
                            identity.userId(),
                            new SocketEvent<>(
                                    SocketType.GAME,
                                    games.sync(identity.userId(), request.matchId)));
                }
                case "subscribe" -> {
                    RoomResponse room = rooms.byId(roomId);
                    rooms.requireMember(room, identity.userId());
                    games.publishRoom(room.code());
                }
                default -> throw BusinessException.of(ValidateCode.GAME_INVALID_INPUT);
            }
        } catch (BusinessException rejected) {
            error(session, rejected.getValidateCode());
        } catch (JsonProcessingException malformed) {
            error(session, ValidateCode.SYS_MALFORMED_JSON);
        } catch (RuntimeException unavailable) {
            log.warn("Lỗi xử lý WebSocket userId={}", identity.userId(), unavailable);
            error(session, ValidateCode.SYS_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Tạo ERROR bằng ApiResponseFactory với locale đã chọn tại handshake. Giữ code/key ổn định cho
     * client xử lý, chỉ dịch message và không trả exception text.
     */
    private void error(WebSocketSession session, ValidateCode code) {
        Locale locale = (Locale) session.getAttributes().get(AppConstants.SOCKET_LOCALE);
        sockets.send(
                identity(session).userId(),
                new SocketEvent<>(
                        SocketType.ERROR, responses.error(code, new Object[0], Map.of(), locale)));
    }

    /**
     * Đọc danh tính đã được auth interceptor đặt vào attributes khi handshake. Client không được
     * cung cấp userId trong payload để thay thế danh tính này.
     */
    private SocketIdentity identity(WebSocketSession session) {
        return (SocketIdentity) session.getAttributes().get(AppConstants.SOCKET_IDENTITY);
    }

    /**
     * Dọn bucket rate và xóa presence theo cặp userId/socketId. Chỉ báo Coordinator.disconnected
     * nếu socket đóng vẫn là kết nối hiện tại; socket cũ bị thay thế không làm mất presence mới.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        windows.remove(session.getId());
        SocketIdentity identity = identity(session);
        if (identity != null && sockets.remove(identity.userId(), session.getId()))
            games.disconnected(identity.userId());
    }
}
