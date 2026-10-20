package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.RuntimeMode;

/**
 * Snapshot gửi cho client vẽ game; thời hạn phục hồi dùng Instant, không nhận state này ngược làm
 * kết quả.
 */
@Schema(description = "Frame game gửi qua WebSocket; state do máy chủ quyết định.")
public record GameFrameResponse(
        @Schema(description = "Snapshot phục vụ vẽ game; không chấp nhận client ghi đè.")
                GameState state,
        @Schema(description = "RUNNING, RECOVERING, SAVING, ENDING, FINISHED hoặc ABORTED.")
                RuntimeMode mode,
        @Schema(description = "Các người đã xác nhận khôi phục.") List<String> resumeReady,
        @Schema(
                        description = "Hạn xác nhận phục hồi theo UTC; null khi không RECOVERING.",
                        type = "string",
                        format = "date-time",
                        example = "2026-10-20T08:15:30Z")
                Instant recoveryDeadline,
        @Schema(
                        description =
                                "Key i18n của trạng thái lưu, rỗng khi bình thường; không phải exception text.")
                String messageKey)
        implements BaseResponse {}
