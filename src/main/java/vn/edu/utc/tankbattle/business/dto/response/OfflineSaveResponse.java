package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

@Schema(description = "Bản lưu cá nhân để đồng bộ khi có mạng; offline thật sẽ lưu IndexedDB ở UI.")
public record OfflineSaveResponse(
        @Schema(description = "Slot 1–3.") int slot,
        @Schema(description = "Phiên bản tăng sau mỗi lần ghi.") long revision,
        @Schema(description = "Thời điểm server nhận bản lưu, UTC.") Instant updatedAt,
        @Schema(description = "Checkpoint, null trong danh sách để tránh tải payload theo lô lớn.")
                GameState state)
        implements BaseResponse {}
