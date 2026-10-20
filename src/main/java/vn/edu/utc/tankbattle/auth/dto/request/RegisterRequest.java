package vn.edu.utc.tankbattle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.FieldsMatch;
import vn.edu.utc.tankbattle.common.validation.ValidText;

/** Đăng ký local; normalize tên trước validation, giữ nguyên mật khẩu. */
@Schema(description = "Đăng ký local; normalize tên trước validation, giữ nguyên mật khẩu.")
@FieldsMatch(
        field = "password",
        confirm = "confirmPassword",
        code = ValidateCode.USER_PASSWORD_MISMATCH)
public class RegisterRequest extends BaseRequest {
    @ValidText(TextKind.USERNAME)
    @Schema(description = "Tên đăng nhập 4–24 chữ/số/gạch dưới.", example = "utc_student")
    private String username = "";

    @ValidText(TextKind.DISPLAY_NAME)
    @Schema(description = "Tên hiển thị hỗ trợ tiếng Việt.", example = "Nguyễn An")
    private String displayName = "";

    @ValidText(TextKind.PASSWORD)
    @Schema(
            description = "Mật khẩu 12–64 ký tự, không strip.",
            example = "MatKhauHocTap!2026",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password = "";

    @NotNull(message = "{user.password.mismatch}")
    @Schema(
            description = "Xác nhận khớp mật khẩu.",
            example = "MatKhauHocTap!2026",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String confirmPassword = "";

    @Override
    public void normalize() {
        username = TextNormalizer.username(username);
        displayName = TextNormalizer.name(displayName);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        displayName = value;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String value) {
        password = value;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String value) {
        confirmPassword = value;
    }
}
