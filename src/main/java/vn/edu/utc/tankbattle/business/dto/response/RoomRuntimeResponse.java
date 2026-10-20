package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Ghép lobby bền vững với tình trạng kết nối/phục hồi trong RAM để client vẽ phòng chờ. */
@Schema(description = "Lobby snapshot và trạng thái kết nối/khôi phục.")
public record RoomRuntimeResponse(
        @Schema(description = "Dữ liệu lobby hiện tại.") RoomResponse room,
        @Schema(description = "UUID của trận, giữ nguyên khi khôi phục.") String matchId,
        @Schema(description = "Danh sách ID đang có kết nối hợp lệ.") List<Long> connectedUserIds,
        @Schema(description = "Các người đã xác nhận khôi phục.") List<String> resumeReady,
        @Schema(
                        description = "Hạn xác nhận phục hồi theo UTC; null khi không RECOVERING.",
                        type = "string",
                        format = "date-time",
                        example = "2026-10-20T08:15:30Z")
                Instant recoveryDeadline)
        implements BaseResponse {}
