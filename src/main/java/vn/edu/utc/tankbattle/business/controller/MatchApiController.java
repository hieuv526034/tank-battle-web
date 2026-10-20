package vn.edu.utc.tankbattle.business.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.business.dto.response.*;
import vn.edu.utc.tankbattle.business.runtime.GameCoordinator;
import vn.edu.utc.tankbattle.business.service.MatchService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.dto.request.PageRequest;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** Chặn truy cập trận người khác, chỉ serialize DTO whitelist thay vì checkpoint nội bộ. */
@RestController
@RequestMapping("/api/matches")
@Tag(name = "04. Trận và lịch sử")
@SecurityRequirement(name = "bearerAuth")
@StandardApiErrors
public class MatchApiController {
    private final MatchService matches;
    private final GameCoordinator games;
    private final ApiResponseFactory responses;

    public MatchApiController(
            MatchService matches, GameCoordinator games, ApiResponseFactory responses) {
        this.matches = matches;
        this.games = games;
        this.responses = responses;
    }

    @GetMapping
    @Operation(
            summary = "Lịch sử trận của tôi",
            description = "Trang từ 0; chỉ lấy trận user đã tham gia, không có payload checkpoint.")
    public ApiResponse<List<MatchSummaryResponse>> history(
            @AuthenticationPrincipal Jwt principal, @Valid @ModelAttribute PageRequest page) {
        return responses.success(
                matches
                        .history(
                                Long.parseLong(principal.getSubject()),
                                page.getPage(),
                                page.getSize())
                        .stream()
                        .map(MatchSummaryResponse::from)
                        .toList());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Metadata trận",
            description = "MATCH_001 không tồn tại, MATCH_002 không phải người tham gia.")
    public ApiResponse<MatchSummaryResponse> detail(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        MatchResponse match = matches.metadata(id);
        matches.requireParticipant(id, Long.parseLong(principal.getSubject()));
        return responses.success(MatchSummaryResponse.from(match));
    }

    @GetMapping("/{id}/results")
    @Operation(
            summary = "Kết quả của từng người",
            description = "Người tham gia mới được đọc; kết quả null khi trận chưa FINISHED.")
    public ApiResponse<List<ResultResponse>> results(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        matches.metadata(id);
        matches.requireParticipant(id, Long.parseLong(principal.getSubject()));
        return responses.success(matches.results(id));
    }

    @GetMapping("/{id}/state")
    @Operation(
            summary = "Đồng bộ snapshot của trận",
            description = "Chỉ người tham gia; dùng khi kết nối lại, server vẫn quyết định state.")
    public ApiResponse<GameFrameResponse> state(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        return responses.success(games.sync(Long.parseLong(principal.getSubject()), id));
    }

    @PostMapping("/{id}/resume-ready")
    @Operation(
            summary = "Xác nhận chơi tiếp sau restart",
            description = "Người còn sống cần kết nối socket và xác nhận trước deadline.")
    public ApiResponse<Void> resume(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        games.resumeReady(Long.parseLong(principal.getSubject()), id);
        return responses.success(null);
    }

    @PostMapping("/{id}/cancel-recovery")
    @Operation(
            summary = "Hủy trận đang phục hồi",
            description = "Chỉ người tham gia còn membership; ABORTED không cộng ranking.")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        games.cancelRecovery(Long.parseLong(principal.getSubject()), id);
        return responses.success(null);
    }
}
