package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Refresh token không xuất hiện trong JSON response. */
@Schema(description = "Access token của ứng dụng. Refresh token chỉ nằm trong HttpOnly cookie.")
public record AuthTokenResponse(
        @Schema(
                        description =
                                "Access JWT dùng Authorization: Bearer. Giữ trong bộ nhớ, không ghi log.")
                String accessToken,
        @Schema(description = "Cơ chế xác thực HTTP.", example = "Bearer") String tokenType,
        @Schema(description = "Số giây còn hiệu lực của access token.", example = "300")
                long expiresIn,
        @Schema(description = "ID tài khoản do server quản lý.", example = "1") long userId,
        @Schema(description = "Tên đăng nhập được chuẩn hóa chữ thường.", example = "utc_student")
                String username)
        implements BaseResponse {}
