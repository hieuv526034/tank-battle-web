package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

@Schema(
        description =
                "Hạng Campaign theo độ khó. Mỗi cửa chỉ lấy điểm cao nhất của các trận WIN đã xác nhận.")
public record CampaignRankResponse(
        @Schema(description = "ID người chơi.") long userId,
        @Schema(description = "Tên hiển thị.") String displayName,
        @Schema(description = "Hạng đồng điểm dùng DENSE_RANK.") long place,
        @Schema(description = "Số cửa đã thắng.") long clearedStages,
        @Schema(description = "Tổng điểm tốt nhất của từng cửa.") long score)
        implements BaseResponse {}
