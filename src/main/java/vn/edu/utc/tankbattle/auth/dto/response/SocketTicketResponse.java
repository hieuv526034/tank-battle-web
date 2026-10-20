package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Ticket opaque một lần, chỉ giữ trong RAM client; không phải JWT và không được ghi log. */
@Schema(description = "Vé WebSocket dùng một lần, hết hạn tối đa 30 giây.")
public record SocketTicketResponse(
        @Schema(description = "Gửi khi handshake /ws/game?ticket=...; dùng một lần.") String ticket,
        @Schema(description = "Hết hạn UTC.", format = "date-time") Instant expiresAt)
        implements BaseResponse {}
