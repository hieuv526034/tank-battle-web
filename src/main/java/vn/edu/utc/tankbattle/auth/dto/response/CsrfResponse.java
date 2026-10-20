package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Token CSRF công khai cho client cùng origin; cookie tương ứng phải đi cùng request POST. */
@Schema(description = "Token chống CSRF cho API dùng cookie; không phải access JWT.")
public record CsrfResponse(
        @Schema(description = "Tên header phải gửi khi POST.", example = "X-XSRF-TOKEN")
                String headerName,
        @Schema(description = "Tên tham số form tương đương.", example = "_csrf")
                String parameterName,
        @Schema(description = "Lấy token từ response thật; không dùng ví dụ cố định.") String token)
        implements BaseResponse {}
