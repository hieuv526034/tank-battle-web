package vn.edu.utc.tankbattle.business.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;

/** Client chỉ gửi ý định; không có userId, tọa độ, HP hoặc kết quả trong request. */
@Schema(description = "Message WebSocket; không phải HTTP API. Client chỉ gửi ý định điều khiển.")
public class SocketRequest extends BaseRequest {
    @NotNull(message = "{game.invalid_input}")
    @Pattern(regexp = "subscribe|input|sync", message = "{game.invalid_input}")
    @Schema(description = "Loại message theo giao thức WebSocket.")
    public String type;

    @Size(max = 8, message = "{room.code.invalid}")
    @Schema(description = "Mã mời phòng; không phải token xác thực.", example = "ABCD2345")
    public String code;

    @Size(max = 36, message = "{game.invalid_input}")
    @Schema(description = "UUID của trận, giữ nguyên khi khôi phục.")
    public String matchId;

    @Min(value = 0, message = "{game.invalid_input}")
    @Schema(description = "Số thứ tự input tăng dần; server bỏ message cũ/trùng.", example = "42")
    public long sequence;

    @Pattern(regexp = "UP|DOWN|LEFT|RIGHT|NONE", message = "{game.invalid_input}")
    @Schema(description = "UP, DOWN, LEFT, RIGHT hoặc NONE.", example = "LEFT")
    public String direction = "NONE";

    @Schema(description = "Ý định giữ phím bắn; cooldown do server kiểm tra.", example = "true")
    public boolean fire;
}
