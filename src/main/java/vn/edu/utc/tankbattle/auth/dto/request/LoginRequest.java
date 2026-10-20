package vn.edu.utc.tankbattle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidText;

/** Đăng nhập bằng mật khẩu local; không dùng cho Google. */
@Schema(description = "Đăng nhập bằng mật khẩu local; không dùng cho Google.")
public class LoginRequest extends BaseRequest {
    @ValidText(TextKind.USERNAME)
    @Schema(description = "Tên đăng nhập nội bộ.", example = "utc_student")
    private String username = "";

    @ValidText(TextKind.PASSWORD)
    @Schema(
            description = "Giữ nguyên mật khẩu đã nhập.",
            example = "MatKhauHocTap!2026",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password = "";

    @Override
    public void normalize() {
        username = TextNormalizer.username(username);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String value) {
        password = value;
    }
}
