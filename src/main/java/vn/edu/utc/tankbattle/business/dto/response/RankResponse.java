package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Một hàng bảng xếp hạng Arena, suy ra từ trận FINISHED và giữ thứ hạng cả khi phân trang. */
@Schema(description = "Một dòng xếp hạng từ kết quả FINISHED, tính trước khi phân trang.")
public record RankResponse(
        @Schema(description = "ID tài khoản do server quản lý.", example = "1") long userId,
        @Schema(
                        description = "Tên hiển thị, không dùng để kiểm tra quyền.",
                        example = "Người chơi UTC")
                String displayName,
        @Schema(description = "Hạng trên toàn bảng; cùng tiêu chí được đồng hạng.", example = "1")
                long rank,
        @Schema(description = "Điểm tích lũy online: 3 mỗi thắng, 1 mỗi hòa.", example = "9")
                long points,
        @Schema(description = "Số trận FINISHED được tính thành tích.", example = "5") long played,
        @Schema(description = "Số trận thắng.", example = "3") long wins,
        @Schema(description = "Số trận thua.", example = "2") long losses,
        @Schema(description = "Số trận hòa.", example = "0") long draws,
        @Schema(description = "Tỷ lệ thắng phần trăm, một chữ số thập phân.", example = "60.0")
                double winRate)
        implements BaseResponse {}
