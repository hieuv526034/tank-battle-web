package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.SocketType;

/** Cùng một envelope cho mọi message server gửi qua WebSocket. */
@Schema(description = "Envelope server gửi qua WebSocket: loại sự kiện và payload có kiểu.")
public record SocketEvent<T>(
        @Schema(description = "Loại message theo giao thức WebSocket.") SocketType type,
        @Schema(description = "Payload có kiểu tương ứng với sự kiện.") T data)
        implements BaseResponse {}
