package vn.edu.utc.tankbattle.business.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidText;

/** Tạo phòng; chủ phòng được lấy từ danh tính đã xác thực. */
@Schema(description = "Tạo phòng; chủ phòng được lấy từ danh tính đã xác thực.")
public class CreateRoomRequest extends BaseRequest {
    @ValidText(TextKind.ROOM_NAME)
    @Schema(description = "Tên phòng 3–40 ký tự, hỗ trợ tiếng Việt.", example = "Phòng học UTC")
    private String name = "";

    @Min(value = AppConstants.MIN_ROOM_PLAYERS, message = "{room.capacity.invalid}")
    @Max(value = AppConstants.MAX_ROOM_PLAYERS, message = "{room.capacity.invalid}")
    @Schema(description = "Số người tối đa trong phòng.", example = "4")
    private int capacity = 4;

    @Override
    public void normalize() {
        name = TextNormalizer.name(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int value) {
        capacity = value;
    }
}
