package vn.edu.utc.tankbattle.business.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;

@Schema(
        description =
                "Bản lưu cá nhân không đáng tin cho ranking. expectedRevision=0 tạo mới, lần sau dùng revision server trả.")
public class OfflineSaveRequest extends BaseRequest {
    @Min(value = 0, message = "{save.invalid}")
    @Schema(description = "Revision đang giữ; mismatch trả SAVE_002.", example = "0")
    private long expectedRevision;

    @NotNull(message = "{save.invalid}")
    @Schema(
            description =
                    "Checkpoint schema 1/2; tối đa 1 triệu ký tự JSON. Không chuyển thành kết quả online.")
    private GameState state;

    public long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(long revision) {
        expectedRevision = revision;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }
}
