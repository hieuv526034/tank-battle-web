package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Phòng và danh sách thành viên đã gom theo lô; hostUserId dùng kiểm tra quyền chủ phòng. */
@Schema(description = "Trạng thái phòng và danh sách thành viên; số ghế không vượt capacity.")
public record RoomResponse(
        @Schema(description = "ID đối tượng theo loại response.") long id,
        @Schema(description = "Mã mời phòng; không phải token xác thực.", example = "ABCD2345")
                String code,
        @Schema(
                        description = "Tên phòng hoặc đối tượng hiển thị theo DTO.",
                        example = "Phòng thực hành UTC")
                String name,
        @Schema(description = "User ID của chủ phòng.", example = "1") long hostUserId,
        @Schema(description = "Số người tối đa trong phòng, từ 2 đến 4.", example = "4")
                int capacity,
        @Schema(description = "Trạng thái nghiệp vụ, dùng enum/quy tắc trong AppConstants.")
                String status,
        @Schema(description = "Danh sách thành viên hiện tại, lấy theo lô để tránh N+1.")
                List<MemberResponse> members)
        implements BaseResponse {}
