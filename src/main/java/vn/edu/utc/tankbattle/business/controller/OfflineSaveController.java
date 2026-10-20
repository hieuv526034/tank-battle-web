package vn.edu.utc.tankbattle.business.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.business.dto.request.OfflineSaveRequest;
import vn.edu.utc.tankbattle.business.dto.response.OfflineSaveResponse;
import vn.edu.utc.tankbattle.business.service.OfflineSaveService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

@RestController
@RequestMapping("/api/offline-saves")
@Tag(name = "07. Đồng bộ bản lưu cá nhân")
@SecurityRequirement(name = "bearerAuth")
@StandardApiErrors
public class OfflineSaveController {
    private final OfflineSaveService saves;
    private final ApiResponseFactory responses;

    public OfflineSaveController(OfflineSaveService saves, ApiResponseFactory responses) {
        this.saves = saves;
        this.responses = responses;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách tối đa ba slot của tôi",
            description = "Chỉ metadata, không tải payload lớn khi liệt kê.")
    public ApiResponse<List<OfflineSaveResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return responses.success(saves.list(Long.parseLong(jwt.getSubject())));
    }

    @GetMapping("/{slot}")
    @Operation(
            summary = "Tải bản lưu của tôi",
            description = "SAVE_001 khi chưa lưu hoặc không phải chủ. Không nhận userId.")
    public ApiResponse<OfflineSaveResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int slot) {
        return responses.success(saves.get(Long.parseLong(jwt.getSubject()), slot));
    }

    @PutMapping("/{slot}")
    @Operation(
            summary = "Tạo/cập nhật bản lưu có revision",
            description =
                    "expectedRevision=0 tạo mới; conflict 409 cần tải lại và cho người dùng chọn bản giữ. Không cộng xếp hạng.")
    public ApiResponse<OfflineSaveResponse> put(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable int slot,
            @Valid @RequestBody OfflineSaveRequest request) {
        return responses.success(saves.put(Long.parseLong(jwt.getSubject()), slot, request));
    }

    @DeleteMapping("/{slot}")
    @Operation(
            summary = "Xóa slot với revision hiện tại",
            description = "Tránh xóa bản mới hơn mà thiết bị khác vừa ghi.")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int slot, @RequestParam long revision) {
        saves.delete(Long.parseLong(jwt.getSubject()), slot, revision);
        return responses.success(null);
    }
}
