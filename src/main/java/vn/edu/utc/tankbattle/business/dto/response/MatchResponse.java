package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;

/**
 * Projection trận và checkpoint dùng trong backend; payload nội bộ phải được loại khỏi API public.
 */
@Schema(
        description =
                "Projection metadata và checkpoint dùng nội bộ, không trả payload trực tiếp qua API public.")
public record MatchResponse(
        @Schema(description = "ID đối tượng theo loại response.") String id,
        @Schema(description = "ID phòng.", example = "1") long roomId,
        @Schema(description = "Mã phòng gắn với trận.", example = "ABCD2345") String roomCode,
        @Schema(description = "Tên phòng tại thời điểm truy vấn.", example = "Phòng thực hành UTC")
                String roomName,
        @Schema(description = "Trạng thái nghiệp vụ, dùng enum/quy tắc trong AppConstants.")
                String status,
        @Schema(description = "Thời điểm bắt đầu dạng ISO-8601.") Instant startedAt,
        @Schema(description = "Thời điểm kết thúc; null khi chưa xong.") Instant endedAt,
        @Schema(description = "Lý do kết thúc hoặc hủy trận.") String endReason,
        @Schema(description = "Hạn xác nhận phục hồi; null khi không RECOVERING.")
                Instant recoveryDeadline,
        @Schema(
                        description =
                                "Checkpoint nội bộ; không đưa nguyên payload vào response public.",
                        hidden = true)
                String payload,
        GameMode mode,
        Difficulty difficulty,
        int stage)
        implements BaseResponse {}
