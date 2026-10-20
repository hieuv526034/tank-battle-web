package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Hồ sơ được phép trả ra ngoài; không chứa hash mật khẩu, identity Google hoặc token. */
@Schema(description = "Thông tin tài khoản của chính người dùng; không có password hash.")
public record UserResponse(
        @Schema(description = "ID đối tượng theo loại response.") long id,
        @Schema(description = "Tên đăng nhập được chuẩn hóa chữ thường.", example = "utc_student")
                String username,
        @Schema(
                        description = "Tên hiển thị, không dùng để kiểm tra quyền.",
                        example = "Người chơi UTC")
                String displayName)
        implements BaseResponse {}
