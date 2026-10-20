package vn.edu.utc.tankbattle.auth.config;

import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.auth.security.AuthErrorHandler;

/**
 * Gắn failure handler trực tiếp để AuthenticationServiceException cũng có JSON custom. Handler mặc
 * định của Bearer filter có thể ném lại lỗi dịch vụ trước khi gọi entry point.
 */
@Component
public class BearerTokenFilterPostProcessor
        implements ObjectPostProcessor<BearerTokenAuthenticationFilter> {
    private final AuthErrorHandler errors;

    /**
     * Nhận AuthErrorHandler dùng chung để gắn lỗi JWT/filter vào cùng hợp đồng code/key/locale của
     * API.
     */
    public BearerTokenFilterPostProcessor(AuthErrorHandler errors) {
        this.errors = errors;
    }

    /**
     * Thay failure handler của Bearer filter bằng nhánh tokenFailure của ứng dụng. Nhánh này phân
     * biệt token sai với lỗi dịch vụ xác thực, giúp lỗi DB không bị trả thành lỗi thông tin đăng
     * nhập.
     *
     * @return cùng filter đã được gắn handler custom
     */
    @Override
    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
        filter.setAuthenticationFailureHandler(errors::tokenFailure);
        return filter;
    }
}
