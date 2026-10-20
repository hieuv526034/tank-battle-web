# Mã lỗi, validation và bản dịch

Nguồn chuẩn là common.constant.AppConstants.ValidateCode. Mỗi enum chứa tên miền nghiệp vụ, code public, message key và HTTP status. Tiền tố: SYS, AUTH, USER, ROOM, MATCH, GAME, SAVE, PAGE. Hiện có 46 mã, kiểm tra duy nhất và đủ bản dịch bằng Gradle/JUnit SourceContractTest.

## Cách dùng

```java
throw BusinessException.of(ValidateCode.ROOM_NOT_FOUND);
throw new AuthFailure(ValidateCode.AUTH_INVALID_REFRESH);
```

Không truyền String chứa câu dịch/code/key vào exception. Constructor chỉ nhận ValidateCode; service có thể truyền arguments khi bản dịch có tham số. Lỗi startup có thể dùng thông báo kỹ thuật cho người vận hành; không trả chúng ra client.

ApiResponseFactory chọn messages.properties/messages_vi.properties/messages_en.properties theo Accept-Language vi/en, mặc định vi. RestControllerAdvice xử lý MVC; SecurityErrorWriter xử lý filter/handshake; socket ERROR cũng dùng cùng envelope. Không trả exception.getMessage(), SQL, rejectedValue hay stacktrace.

Header đúng là `Accept-Language`, không phải `locate`. Ví dụ `Accept-Language: en` hoặc `Accept-Language: vi`. Thiếu header dùng vi; en-US về en; fr không được hỗ trợ nên về vi; `fr;q=1.0,en;q=0.8` chọn en. HTTP response công bố `Content-Language` và body.locale theo ngôn ngữ thực dùng.

Handshake WebSocket dùng cùng AcceptHeaderLocaleResolver với MVC/security. Locale được chốt trong session socket; muốn đổi ngôn ngữ phải kết nối lại. Swagger có ô header trên mọi REST operation, cấu hình tập trung ở OpenApiConfig; cấu hình ngôn ngữ hỗ trợ/mặc định ở AppConstants. BusinessFlowTest kiểm tra sáu trường hợp header trên success/business/validation/security và lỗi socket vi/en.

## Response lỗi

```json
{
  "code": "SYS_002",
  "messageKey": "system.invalid_request",
  "message": "Kiểm tra lại dữ liệu nhập.",
  "locale": "vi",
  "data": null,
  "errors": {
    "username": {
      "code": "USER_002",
      "messageKey": "user.username.invalid",
      "message": "Thông báo đã dịch"
    }
  }
}
```

HTTP status phải đúng theo enum, không trả HTTP 200 cho lỗi nghiệp vụ. API thành công SYS_000; field errors là map theo tên trường. Không có lỗi trường thì errors rỗng.

## Validation dùng chung

BaseRequest có normalize; RequestNormalizationAdvice gọi trước Bean Validation. Username/tên/mã phòng qua TextNormalizer; mật khẩu giữ nguyên. ValidText chọn TextKind, TextConstraintValidator/ValidationRules dùng cùng rule cho DTO và invariant ở service.

Username 4–24 chữ/số/gạch dưới; tên hiển thị 2–30; tên phòng 3–40; mã phòng 8 ký tự theo alphabet cấu hình. Password 12–64 và confirm khớp. Page 0–10000, size 1–50; limit tại DTO để không yêu cầu list vô hạn.

Enum/query parameter sai hoặc thiếu tham số trả SYS_002; JSON sai trả SYS_003. Tranh chấp dữ liệu SYS_005; DB ngoài dự kiến SYS_006, không đổ lỗi do người dùng nhập sai. Request thô bị container từ chối trước Spring (ví dụ URL sai chuẩn HTTP) có thể do container xử lý, không hứa mọi lỗi giao thức đều qua MVC.

## Mã mới của gameplay và lưu

| Code     | Key                    | Ý nghĩa                                   |
| -------- | ---------------------- | ----------------------------------------- |
| GAME_001 | game.invalid_input     | Input/cấu hình lựa chọn trận không hợp lệ |
| GAME_002 | game.stage_locked      | Chưa thắng cửa trước cùng độ khó          |
| SAVE_001 | save.not_found         | Không có slot thuộc tài khoản             |
| SAVE_002 | save.revision_conflict | Bản lưu đã bị thiết bị khác thay đổi      |
| SAVE_003 | save.invalid           | Slot/state/revision không hợp lệ          |

Bảng đầy đủ đọc enum và bundle, không tạo catalog trùng trong service.

## Thêm lỗi mới

Thêm enum với code/key chưa dùng → đủ ba bundle → gắn validator/exception → mô tả Operation/Swagger → kiểm thử case và locale. Không tái sử dụng code đã phát hành cho nghĩa khác. Gradle/JUnit SourceContractTest kiểm tra code/key/bundle và không còn exception nhận String; JUnit kiểm tra HTTP/filter thật.
