package vn.edu.utc.tankbattle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidText;

@Schema(description = "Đổi tên hiển thị của chính mình; không đổi username/role.")
public class UpdateProfileRequest extends BaseRequest {
    @ValidText(value = TextKind.DISPLAY_NAME, message = "{user.display_name.invalid}")
    @Schema(description = "Tên hiển thị 2–30 ký tự sau chuẩn hóa.", example = "Sinh viên UTC")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public void normalize() {
        displayName = TextNormalizer.name(displayName);
    }
}
