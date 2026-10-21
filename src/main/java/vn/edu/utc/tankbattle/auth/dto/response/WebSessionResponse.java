package vn.edu.utc.tankbattle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Chỉ trả trạng thái phiên và CSRF; không nhúng JWT hoặc danh tính vào HTML được cache. */
@Schema(description = "Trạng thái phiên web và CSRF cho form/trao đổi session; không được cache.")
public record WebSessionResponse(
        boolean authenticated, boolean googleEnabled, String csrfHeaderName, String csrfToken)
        implements BaseResponse {}
