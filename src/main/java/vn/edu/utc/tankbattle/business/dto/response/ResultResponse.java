package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/**
 * Kết quả chính thức của một người trong trận; lấy từ dữ liệu đã commit, không tin kết quả client
 * tự khai.
 */
@Schema(description = "Kết quả chính thức của một người trong trận đã kết thúc.")
public record ResultResponse(
        @Schema(description = "ID tài khoản do server quản lý.", example = "1") long userId,
        @Schema(
                        description = "Tên hiển thị, không dùng để kiểm tra quyền.",
                        example = "Người chơi UTC")
                String displayName,
        @Schema(description = "WIN, LOSS, DRAW hoặc null nếu chưa có kết quả.", example = "WIN")
                String outcome,
        @Schema(description = "HP còn lại khi kết thúc.", example = "2") Integer hp,
        @Schema(description = "Số mục tiêu diệt được, do server đếm.") int kills,
        @Schema(description = "Tổng điểm diệt mục tiêu, không phải điểm thắng/hòa Arena.")
                int score)
        implements BaseResponse {}
