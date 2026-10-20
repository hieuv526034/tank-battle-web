package vn.edu.utc.tankbattle.business.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Thông tin thành viên hiện tại của phòng; seat/ready dùng để kiểm tra điều kiện bắt đầu. */
@Schema(description = "Thông tin hiển thị của một thành viên phòng.")
public record MemberResponse(
        @Schema(description = "ID tài khoản do server quản lý.", example = "1") long userId,
        @Schema(
                        description = "Tên hiển thị, không dùng để kiểm tra quyền.",
                        example = "Người chơi UTC")
                String displayName,
        @Schema(description = "Chỉ số ghế từ 0 đến 3.", example = "0") int seat,
        @Schema(description = "Trạng thái sẵn sàng của chính thành viên.", example = "true")
                boolean ready)
        implements BaseResponse {}
