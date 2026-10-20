package vn.edu.utc.tankbattle.business.model;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;

/** Projection nội bộ gom member của nhiều room sau một query. */
@Schema(description = "Projection nội bộ gom thành viên theo roomId, không phải API public.")
public record RoomMemberRow(
        @Schema(description = "ID phòng.", example = "1") long roomId, MemberResponse member) {}
