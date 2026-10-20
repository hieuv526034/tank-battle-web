package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

@Schema(
        description =
                "Luật/catalog hiện tại để UI hiển thị và tải cho offline; trận đã mở giữ bản cấu hình trong checkpoint.")
public record GameCatalogResponse(
        @Schema(description = "Luật mô phỏng.") GameRules rules,
        @Schema(description = "Địch/item.") GameContentDefinition content,
        @Schema(description = "Danh sách cửa/wave.") List<CampaignStage> stages)
        implements BaseResponse {}
