package vn.edu.utc.tankbattle.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/**
 * Lỗi của một trường nhập liệu, gồm mã/key/thông báo đã dịch; không đưa giá trị bị từ chối ra
 * ngoài.
 */
@Schema(description = "Lỗi một trường; code/key giữ nguyên giữa các ngôn ngữ.")
public record FieldViolationResponse(
        @Schema(example = "USER_002", description = "Mã theo domain của trường.") String code,
        @Schema(example = "user.username.invalid", description = "Key trong bundle i18n.")
                String messageKey,
        @Schema(description = "Thông báo đã dịch theo locale của request.") String message)
        implements BaseResponse {}
