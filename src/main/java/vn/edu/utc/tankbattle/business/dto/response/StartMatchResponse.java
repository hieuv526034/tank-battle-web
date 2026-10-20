package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

@Schema(description = "Trận đã tạo và commit thành công; runtime dùng cùng matchId khi khôi phục.")
public record StartMatchResponse(
        @Schema(
                        description = "UUID trận; dùng để sync/input/resume.",
                        example = "37fb2e54-5cf2-4c7c-8124-832cd55a1760")
                String matchId)
        implements BaseResponse {}
