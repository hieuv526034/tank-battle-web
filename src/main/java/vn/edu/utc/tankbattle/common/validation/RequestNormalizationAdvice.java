package vn.edu.utc.tankbattle.common.validation;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;

/** Gọi normalize sau Jackson, trước @Valid. Không sanitize HTML hay đổi mật khẩu ở đây. */
@ControllerAdvice
public class RequestNormalizationAdvice extends RequestBodyAdviceAdapter {
    /**
     * Chỉ áp normalize cho DTO kế thừa BaseRequest khi MVC đọc request body. Các kiểu khác giữ quy
     * trình đọc của converter đang dùng.
     */
    @Override
    public boolean supports(
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return BaseRequest.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * Gọi normalize ngay sau Jackson deserialize và trước khi @Valid chạy. Mỗi request tự xác định
     * trường tên/định danh cần chuẩn hóa; password được giữ nguyên theo hợp đồng DTO.
     */
    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage input,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof BaseRequest request) {
            request.normalize();
        }
        return body;
    }
}
