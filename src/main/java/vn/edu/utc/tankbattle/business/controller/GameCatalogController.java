package vn.edu.utc.tankbattle.business.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.business.config.*;
import vn.edu.utc.tankbattle.business.dto.response.GameCatalogResponse;
import vn.edu.utc.tankbattle.business.engine.StateCodec;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

@RestController
@RequestMapping("/api/game")
@Tag(name = "06. Nội dung game")
@SecurityRequirement(name = "bearerAuth")
@StandardApiErrors
public class GameCatalogController {
    private final StateCodec codec;
    private final GameContentLoader content;
    private final CampaignCatalog campaign;
    private final ApiResponseFactory responses;

    public GameCatalogController(
            StateCodec codec,
            GameContentLoader content,
            CampaignCatalog campaign,
            ApiResponseFactory responses) {
        this.codec = codec;
        this.content = content;
        this.campaign = campaign;
        this.responses = responses;
    }

    @GetMapping("/catalog")
    @Operation(
            summary = "Đọc luật, địch, item và cửa",
            description =
                    "Chỉ đọc. Thay cấu hình JSON cần restart; không đổi luật của trận đã lưu.")
    public ApiResponse<GameCatalogResponse> catalog() {
        return responses.success(
                new GameCatalogResponse(codec.rules(), content.definition(), campaign.stages()));
    }
}
