package vn.edu.utc.tankbattle.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.auth.dto.request.UpdateProfileRequest;
import vn.edu.utc.tankbattle.auth.dto.response.UserResponse;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Hồ sơ tài khoản thuộc auth, business chỉ dùng userId khi kiểm tra quyền. */
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

    @PutMapping
    @Operation(
            summary = "Đổi tên hiển thị",
            description =
                    "Bearer API, không nhận userId/role; tên được chuẩn hóa trước validation.")
    public ApiResponse<UserResponse> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        users.rename(Long.parseLong(jwt.getSubject()), request.getDisplayName());
        return responses.success(users.byUsername(jwt.getClaimAsString("username")));
    }
}
