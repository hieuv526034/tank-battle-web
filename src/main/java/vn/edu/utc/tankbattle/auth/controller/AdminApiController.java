package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.utc.tankbattle.auth.dto.response.AdminProfileResponse;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Nhóm API quản trị; SecurityConfig bắt buộc ROLE_ADMIN cho toàn bộ /api/admin/**. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "02. Quản trị", description = "Yêu cầu Bearer JWT có vai trò ADMIN.")
@StandardApiErrors
public class AdminApiController {
    private final AccountService accounts;
    private final ApiResponseFactory responses;

    /** Dùng service auth và envelope chung; controller không tự truy cập repository. */
    public AdminApiController(AccountService accounts, ApiResponseFactory responses) {
        this.accounts = accounts;
        this.responses = responses;
    }

    /** Trả hồ sơ sau khi filter đã xác thực JWT và phân quyền ADMIN, không trả cấu hình bí mật. */
    @GetMapping("/me")
    @Operation(
            summary = "Kiểm tra tài khoản và quyền quản trị hiện tại",
            description =
                    "Cần Bearer JWT của ADMIN. USER nhận 403/AUTH_003; chưa đăng nhập nhận 401/AUTH_001. GET không cần CSRF. Accept-Language chọn ngôn ngữ envelope.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<AdminProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        UserEntity user = accounts.credentials(jwt.getClaimAsString("username"));
        return responses.success(
                new AdminProfileResponse(
                        user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    }
}
