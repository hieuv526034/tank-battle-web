package vn.edu.utc.tankbattle.business.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;

/** Trạng thái sẵn sàng của chính thành viên hiện tại. */
@Schema(description = "Bật hoặc tắt sẵn sàng của chính người gửi.")
public class ReadyRequest extends BaseRequest {
    @Schema(description = "Trạng thái sẵn sàng của chính thành viên.", example = "true")
    private boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean value) {
        ready = value;
    }
}
