package vn.edu.utc.tankbattle.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import vn.edu.utc.tankbattle.common.base.response.BaseResponse;

/** Envelope chuẩn, tạo qua ApiResponseFactory để luôn chọn đúng locale. */
public record ApiResponse<T>(
        @Schema(description = "Mã public ổn định theo domain.", example = "ROOM_001") String code,
        @Schema(description = "Key i18n, không phải câu thông báo.", example = "room.not_found")
                String messageKey,
        @Schema(description = "Thông báo đã dịch.", example = "Phòng không tồn tại.")
                String message,
        @Schema(description = "Ngôn ngữ thực tế của response.", example = "vi") String locale,
        @Schema(description = "Payload có kiểu; null khi lỗi.") T data,
        @Schema(description = "Map tên trường sang lỗi code/key/message; không chứa rejectedValue.")
                Map<String, FieldViolationResponse> errors)
        implements BaseResponse {}
