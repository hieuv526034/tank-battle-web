package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.UserRole;

/** Thông tin quản trị hiện tại, không chứa mật khẩu/hash hoặc cấu hình bí mật hệ thống. */
@Schema(description = "Tài khoản quản trị đã xác thực, dùng để kiểm tra quyền truy cập API admin.")
public record AdminProfileResponse(
        @Schema(description = "ID tài khoản.", example = "1") long id,
        @Schema(description = "Username quản trị.", example = "admin") String username,
        @Schema(description = "Tên hiển thị.", example = "Quản trị hệ thống") String displayName,
        @Schema(description = "Vai trò được đọc từ database.", example = "ADMIN") UserRole role)
        implements BaseResponse {}
