package vn.edu.utc.tankbattle.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.dto.FieldViolationResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/**
 * Handler chung cho REST; lỗi ở SecurityFilterChain dùng cùng factory qua SecurityErrorWriter.
 * Không trả SQL, exception message, stack trace hoặc rejectedValue như mật khẩu ra client.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiResponseFactory responses;

    /**
     * Nhận factory dịch mã lỗi dùng chung với security/socket. Spring tạo advice này để xử lý
     * exception từ các REST controller; service không tự tạo HTTP response.
     */
    public ApiExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    /**
     * Chuyển BusinessException thành HTTP status, mã và thông điệp theo ValidateCode. Giữ arguments
     * phục vụ bản dịch có tham số; locale được MVC lấy từ Accept-Language. Response không cache và
     * công bố ngôn ngữ thực dùng qua Content-Language.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException error, Locale locale) {
        ValidateCode code = error.getValidateCode();
        ApiResponse<Void> body = responses.error(code, error.getArguments(), Map.of(), locale);
        return ResponseEntity.status(code.getHttpStatus())
                .header("Cache-Control", "no-store")
                .header("Content-Language", body.locale())
                .body(body);
    }

    /**
     * Gom lỗi bind/Bean Validation của DTO thành errors theo tên trường. Lấy message template làm
     * key để tra ValidateCode thay vì dùng câu lỗi tự do; mỗi trường chỉ giữ lỗi đầu tiên và không
     * đưa giá trị nhập, đặc biệt mật khẩu, vào response. Lỗi binding không có constraint được quy
     * về SYS_INVALID_REQUEST.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> validation(BindException error, Locale locale) {
        Map<String, FieldViolationResponse> fields = new LinkedHashMap<>();
        for (FieldError field : error.getFieldErrors()) {
            ValidateCode code = ValidateCode.SYS_INVALID_REQUEST;
            try {
                ConstraintViolation<?> violation = field.unwrap(ConstraintViolation.class);
                code = codeFromTemplate(violation.getMessageTemplate());
            } catch (IllegalArgumentException bindingFailure) {
                // Lỗi binding/type mismatch không có ConstraintViolation bên trong.
            }
            fields.putIfAbsent(field.getField(), responses.violation(code, locale));
        }
        return response(ValidateCode.SYS_INVALID_REQUEST, fields, locale);
    }

    /**
     * Xử lý constraint được ném ngoài bước bind DTO, ví dụ validation ở tham số service. Property
     * path trở thành key của errors; mỗi constraint được đổi từ message key sang mã chung và dịch
     * cùng locale của request.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraint(
            ConstraintViolationException error, Locale locale) {
        Map<String, FieldViolationResponse> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : error.getConstraintViolations()) {
            fields.putIfAbsent(
                    violation.getPropertyPath().toString(),
                    responses.violation(codeFromTemplate(violation.getMessageTemplate()), locale));
        }
        return response(ValidateCode.SYS_INVALID_REQUEST, fields, locale);
    }

    /**
     * Trả SYS_MALFORMED_JSON khi Jackson không đọc được body, kể cả sai cấu trúc/kiểu enum trong
     * JSON. Không trả nội dung exception vì có thể chứa một phần dữ liệu người dùng gửi.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformed(Locale locale) {
        return response(ValidateCode.SYS_MALFORMED_JSON, Map.of(), locale);
    }

    /**
     * Quy lỗi query/path/header không hợp lệ hoặc thiếu tham số về SYS_INVALID_REQUEST. Đây là lỗi
     * tham số HTTP trước khi service chạy; không tạo mã hoặc thông báo riêng cho từng exception của
     * Spring.
     */
    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> invalidParameters(Locale locale) {
        return response(ValidateCode.SYS_INVALID_REQUEST, Map.of(), locale);
    }

    /**
     * Chuyển vi phạm ràng buộc DB chưa được service xử lý thành SYS_DATA_CONFLICT (HTTP 409).
     * Unique/foreign-key có thể là lớp bảo vệ cuối khi hai request cạnh tranh; không để tên bảng,
     * SQL hoặc constraint lộ ra client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(Locale locale) {
        return response(ValidateCode.SYS_DATA_CONFLICT, Map.of(), locale);
    }

    /**
     * Trả SYS_SERVICE_UNAVAILABLE cho lỗi truy cập DB ngoài dự kiến, ví dụ mất kết nối. Phân biệt
     * lỗi hạ tầng với dữ liệu nhập sai để client biết cần thử lại; response không chứa chi tiết
     * driver.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> database(Locale locale) {
        return response(ValidateCode.SYS_SERVICE_UNAVAILABLE, Map.of(), locale);
    }

    /**
     * Điểm chặn cuối cho exception REST chưa có handler cụ thể: ghi tên loại lỗi và trả
     * SYS_INTERNAL_ERROR. Không gửi message/stack trace ra client; các handler nghiệp vụ và DB phía
     * trên được Spring ưu tiên theo loại exception.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception error, Locale locale) {
        log.error("Unhandled API exception type={}", error.getClass().getName());
        return response(ValidateCode.SYS_INTERNAL_ERROR, Map.of(), locale);
    }

    /**
     * Bỏ dấu ngoặc của template Bean Validation, ví dụ {user.username.invalid}, rồi tra enum theo
     * message key. ValidateCode.fromMessageKey cung cấp mã mặc định nếu template chưa được khai
     * báo.
     */
    private ValidateCode codeFromTemplate(String template) {
        return ValidateCode.fromMessageKey(template.replace("{", "").replace("}", ""));
    }

    /**
     * Dựng response lỗi thống nhất từ enum và danh sách lỗi trường: HTTP status lấy từ enum, nội
     * dung dịch qua factory, no-store và Content-Language đi cùng body. Helper không xử lý
     * transaction hay đọc database.
     */
    private ResponseEntity<ApiResponse<Void>> response(
            ValidateCode code, Map<String, FieldViolationResponse> fields, Locale locale) {
        ApiResponse<Void> body = responses.error(code, new Object[0], fields, locale);
        return ResponseEntity.status(code.getHttpStatus())
                .header("Cache-Control", "no-store")
                .header("Content-Language", body.locale())
                .body(body);
    }
}
