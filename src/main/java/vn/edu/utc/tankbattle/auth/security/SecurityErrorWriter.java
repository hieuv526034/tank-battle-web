package vn.edu.utc.tankbattle.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/**
 * Filter chạy trước MVC nên tự resolve Accept-Language từ request. Dùng chung envelope và
 * MessageSource với controller, không tự ghép JSON/text lỗi.
 */
@Component
public class SecurityErrorWriter {
    private final ObjectMapper json;
    private final ApiResponseFactory responses;
    private final AcceptHeaderLocaleResolver locales;

    /** Nhận mapper JSON, factory response và cùng locale resolver đang dùng cho MVC. */
    public SecurityErrorWriter(
            ObjectMapper json, ApiResponseFactory responses, AcceptHeaderLocaleResolver locales) {
        this.json = json;
        this.responses = responses;
        this.locales = locales;
    }

    /**
     * Ghi lỗi JSON từ filter/handshake, nơi RestControllerAdvice chưa xử lý request. Resolve
     * Accept-Language trực tiếp, đặt HTTP status theo ValidateCode, Content-Language và no-store
     * rồi serialize cùng envelope với controller. Bỏ qua nếu response đã committed để không ghi
     * chồng body/headers.
     */
    public void write(HttpServletRequest request, HttpServletResponse response, ValidateCode code)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Locale locale = locales.resolveLocale(request);
        response.setStatus(code.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Language", locale.getLanguage());
        json.writeValue(
                response.getOutputStream(), responses.error(code, new Object[0], Map.of(), locale));
    }
}
