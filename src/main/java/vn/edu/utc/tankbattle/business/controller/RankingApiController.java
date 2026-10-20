package vn.edu.utc.tankbattle.business.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.business.dto.response.CampaignRankResponse;
import vn.edu.utc.tankbattle.business.dto.response.RankResponse;
import vn.edu.utc.tankbattle.business.service.RankingService;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.dto.request.PageRequest;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Ranking chỉ lấy kết quả FINISHED do server lưu; không nhận điểm tự khai từ client. */
@RestController
@RequestMapping("/api/rankings")
@Tag(name = "05. Xếp hạng")
@StandardApiErrors
public class RankingApiController {
    private final RankingService rankings;
    private final ApiResponseFactory responses;

    public RankingApiController(RankingService rankings, ApiResponseFactory responses) {
        this.rankings = rankings;
        this.responses = responses;
    }

    @GetMapping
    @Operation(
            summary = "Bảng xếp hạng Arena",
            description = "Khách được xem. Thắng 3, hòa 1, thua 0; tính hạng trước khi phân trang.")
    public ApiResponse<List<RankResponse>> list(@Valid @ModelAttribute PageRequest page) {
        return responses.success(rankings.page(page.getPage(), page.getSize()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Hạng của chính tôi", description = "data=null khi chưa có trận FINISHED.")
    public ApiResponse<RankResponse> personal(@AuthenticationPrincipal Jwt principal) {
        return responses.success(
                rankings.personal(Long.parseLong(principal.getSubject())).orElse(null));
    }

    @GetMapping("/campaign")
    @Operation(
            summary = "Hạng Campaign theo độ khó",
            description =
                    "Khách được xem. Số cửa thắng giảm dần, rồi tổng điểm tốt nhất từng cửa. Chỉ trận online FINISHED/WIN.")
    public ApiResponse<List<CampaignRankResponse>> campaign(
            @RequestParam(defaultValue = "NORMAL") Difficulty difficulty,
            @Valid @ModelAttribute PageRequest page) {
        return responses.success(
                rankings.campaign(difficulty.name(), page.getPage(), page.getSize()));
    }

    @GetMapping("/campaign/me/progress")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Các cửa đã thắng của tôi",
            description =
                    "Được mở cửa 1 và cửa liền sau cửa đã thắng cùng độ khó; mọi thành viên cần mở khóa khi chơi hợp tác.")
    public ApiResponse<List<Integer>> progress(
            @AuthenticationPrincipal Jwt principal,
            @RequestParam(defaultValue = "NORMAL") Difficulty difficulty) {
        return responses.success(
                rankings.clearedStages(Long.parseLong(principal.getSubject()), difficulty.name()));
    }

    @GetMapping("/campaign/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Hạng Campaign của tôi",
            description =
                    "Tính hạng toàn bảng trước khi lọc user; null khi chưa WIN cửa nào ở độ khó này.")
    public ApiResponse<CampaignRankResponse> campaignPersonal(
            @AuthenticationPrincipal Jwt principal,
            @RequestParam(defaultValue = "NORMAL") Difficulty difficulty) {
        return responses.success(
                rankings.campaignPersonal(Long.parseLong(principal.getSubject()), difficulty.name())
                        .orElse(null));
    }
}
