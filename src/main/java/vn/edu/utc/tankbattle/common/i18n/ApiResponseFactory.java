package vn.edu.utc.tankbattle.common.i18n;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.dto.FieldViolationResponse;

/** Cùng factory cho controller, validation và filter security. Không hardcode text response. */
@Component
public class ApiResponseFactory {
    private final MessageSource messages;

    /** Nhận MessageSource của ứng dụng để tất cả response cùng đọc một catalog bản dịch. */
    public ApiResponseFactory(MessageSource messages) {
        this.messages = messages;
    }

    /**
     * Tạo envelope SYS_SUCCESS theo locale đang được MVC gắn trong LocaleContextHolder. Giữ payload
     * có kiểu và errors rỗng; message thành công cũng theo Accept-Language.
     */
    public <T> ApiResponse<T> success(T data) {
        Locale locale = supported(LocaleContextHolder.getLocale());
        ValidateCode code = ValidateCode.SYS_SUCCESS;
        return new ApiResponse<>(
                code.getCode(),
                code.getMessageKey(),
                text(code, locale),
                locale.getLanguage(),
                data,
                Map.of());
    }

    /**
     * Tạo envelope lỗi từ enum, arguments và map field violations đã chọn. Chuẩn hóa locale hỗ trợ,
     * dịch message và giữ code/key độc lập ngôn ngữ. HTTP status do handler/writer đặt theo cùng
     * enum.
     */
    public ApiResponse<Void> error(
            ValidateCode code,
            Object[] arguments,
            Map<String, FieldViolationResponse> fields,
            Locale requestedLocale) {
        Locale locale = supported(requestedLocale);
        return new ApiResponse<>(
                code.getCode(),
                code.getMessageKey(),
                text(code, locale, arguments),
                locale.getLanguage(),
                null,
                Map.copyOf(fields));
    }

    /**
     * Tạo lỗi của một trường gồm code/key/message đã dịch, dùng cho Bean Validation và lỗi binding.
     */
    public FieldViolationResponse violation(ValidateCode code, Locale locale) {
        return new FieldViolationResponse(
                code.getCode(), code.getMessageKey(), text(code, supported(locale)));
    }

    /**
     * Tra messageKey của ValidateCode trong bundle vi/en và thay arguments nếu có. Key/code nằm
     * trong enum để service không phải tự ghép thông báo.
     */
    public String text(ValidateCode code, Locale locale, Object... arguments) {
        return messages.getMessage(code.getMessageKey(), arguments, supported(locale));
    }

    /**
     * Rút locale vùng như en-US về ngôn ngữ hỗ trợ và dùng vi nếu thiếu/không hỗ trợ. Danh sách
     * ngôn ngữ và mặc định lấy từ AppConstants, thống nhất với LocaleConfig và Swagger.
     */
    private Locale supported(Locale locale) {
        String language = locale == null ? AppConstants.DEFAULT_LANGUAGE : locale.getLanguage();
        return Locale.forLanguageTag(
                AppConstants.SUPPORTED_LANGUAGES.contains(language)
                        ? language
                        : AppConstants.DEFAULT_LANGUAGE);
    }
}
