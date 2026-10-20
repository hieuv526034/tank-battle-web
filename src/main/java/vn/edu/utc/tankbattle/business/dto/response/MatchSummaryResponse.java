package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;

/** Whitelist metadata public; checkpoint payload tuyệt đối không serialize qua API lịch sử. */
@Schema(description = "Metadata trận dành cho người tham gia; không chứa checkpoint payload.")
public record MatchSummaryResponse(
        @Schema(description = "UUID trận.") String id,
        @Schema(description = "Mã phòng.") String roomCode,
        @Schema(description = "Tên phòng.") String roomName,
        @Schema(description = "RUNNING, RECOVERING, FINISHED hoặc ABORTED.") String status,
        @Schema(description = "Mốc bắt đầu UTC.", format = "date-time") Instant startedAt,
        @Schema(description = "Mốc kết thúc UTC, null khi đang chơi.", format = "date-time")
                Instant endedAt,
        @Schema(description = "Lý do kết thúc/hủy.") String endReason,
        @Schema(description = "Hạn xác nhận phục hồi UTC.", format = "date-time")
                Instant recoveryDeadline,
        @Schema(description = "ARENA hoặc CAMPAIGN do server xác nhận.") GameMode mode,
        @Schema(description = "Độ khó đã chọn trước trận.") Difficulty difficulty,
        @Schema(description = "Cửa đã chơi, 1–3.") int stage)
        implements BaseResponse {
    public static MatchSummaryResponse from(MatchResponse internal) {
        return new MatchSummaryResponse(
                internal.id(),
                internal.roomCode(),
                internal.roomName(),
                internal.status(),
                internal.startedAt(),
                internal.endedAt(),
                internal.endReason(),
                internal.recoveryDeadline(),
                internal.mode(),
                internal.difficulty(),
                internal.stage());
    }
}
