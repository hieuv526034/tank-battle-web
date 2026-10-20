package vn.edu.utc.tankbattle.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.edu.utc.tankbattle.auth.dto.response.SocketTicketResponse;
import vn.edu.utc.tankbattle.auth.model.SocketTicket;
import vn.edu.utc.tankbattle.auth.repository.AuthTokenRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/** Vé ngẫu nhiên dùng một lần; browser không phải đưa JWT vào URL WebSocket. */
@Service
public class SocketTicketService {
    private final ConcurrentHashMap<String, SocketTicket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final AuthTokenRepository sessions;

    /** Nhận repository phiên để kiểm tra sid còn hiệu lực khi đổi ticket thành kết nối. */
    public SocketTicketService(AuthTokenRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * Sinh vé ngẫu nhiên một lần từ JWT đang được filter xác thực. Vé lưu trong RAM tối đa 30 giây
     * và không quá hạn access JWT; giới hạn số vé giúp chặn tích lũy bộ nhớ.
     *
     * @param jwt access token đã kiểm chứng ở SecurityFilterChain
     * @return vé opaque và hạn dùng để trình duyệt mở WebSocket
     */
    public SocketTicketResponse issue(Jwt jwt) {
        if (tickets.size() >= 10000) throw BusinessException.of(ValidateCode.SYS_TOO_MANY_REQUESTS);
        Instant expiry = jwt.getExpiresAt();
        Instant deadline = Instant.now().plusSeconds(30);
        if (expiry.isBefore(deadline)) deadline = expiry;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SocketIdentity identity =
                new SocketIdentity(
                        Long.parseLong(jwt.getSubject()),
                        jwt.getClaimAsString("username"),
                        jwt.getClaimAsString("sid"),
                        expiry);
        tickets.put(ticket, new SocketTicket(identity, deadline));
        return new SocketTicketResponse(ticket, deadline);
    }

    /**
     * Lấy rồi xóa ticket bằng thao tác remove nguyên tử, nên hai handshake không cùng dùng được một
     * vé. Kiểm tra hạn vé và sid còn active bằng một lookup DB tại handshake; business chỉ nhận
     * SocketIdentity sau bước này. Ticket sai/hết hạn/đã dùng đều trả AUTH_INVALID_TOKEN.
     */
    public SocketIdentity consume(String ticket) {
        if (ticket == null || ticket.length() != 43)
            throw BusinessException.of(ValidateCode.AUTH_INVALID_TOKEN);
        SocketTicket stored = tickets.remove(ticket);
        if (stored == null
                || !stored.deadline().isAfter(Instant.now())
                || !sessions.active(
                        stored.identity().authSessionId(), stored.identity().userId())) {
            throw BusinessException.of(ValidateCode.AUTH_INVALID_TOKEN);
        }
        return stored.identity();
    }

    /**
     * Thu gom vé chưa dùng nhưng đã hết hạn mỗi 30 giây để giải phóng RAM. Việc xóa không ảnh hưởng
     * socket đã mở, vì socket giữ identity và hạn access riêng.
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanup() {
        tickets.entrySet().removeIf(entry -> !entry.getValue().deadline().isAfter(Instant.now()));
    }
}
