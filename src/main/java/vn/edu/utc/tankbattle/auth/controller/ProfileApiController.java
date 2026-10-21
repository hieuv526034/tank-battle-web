package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.auth.dto.response.UserResponse;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Hồ sơ chỉ đọc thuộc auth; không có luồng cập nhật tên/username/quyền từ người chơi. */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "02. Hồ sơ")
@SecurityRequirement(name = "bearerAuth")
@StandardApiErrors
public class ProfileApiController {
    private final AccountService users;
    private final ApiResponseFactory responses;

    public ProfileApiController(AccountService users, ApiResponseFactory responses) {
        this.users = users;
        this.responses = responses;
    }

    /** Đọc lại dữ liệu từ DB của chính danh tính JWT, không nhận userId từ client. */
    @GetMapping
    @Operation(
            summary = "Xem hồ sơ của tôi",
            description =
                    "Bearer API. Thông tin tài khoản chỉ được xem, không chỉnh sửa sau khi tạo.")
    public ApiResponse<UserResponse> read(@AuthenticationPrincipal Jwt jwt) {
        return responses.success(users.byUsername(jwt.getClaimAsString("username")));
    }

    /** Chặn cả client cũ gọi PUT; không bind body hoặc thực hiện bất kỳ câu UPDATE nào. */
    @PutMapping
    @Operation(
            summary = "Từ chối sửa hồ sơ",
            description =
                    "Endpoint cũ được giữ để trả lỗi chuẩn HTTP 403/USER_007 (vi/en). "
                            + "Mọi tài khoản, kể cả ADMIN, không được tự sửa tên/username/role.")
    public ApiResponse<Void> rejectUpdate() {
        throw BusinessException.of(ValidateCode.USER_PROFILE_READ_ONLY);
    }
}
