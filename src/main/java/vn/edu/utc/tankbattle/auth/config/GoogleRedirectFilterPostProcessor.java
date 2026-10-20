package vn.edu.utc.tankbattle.auth.config;

import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.auth.security.AuthErrorHandler;

/** Lỗi khởi tạo redirect Google phải cùng format với lỗi callback Google. */
@Component
public class GoogleRedirectFilterPostProcessor
        implements ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter> {
    private final AuthErrorHandler errors;

    /** Nhận handler lỗi OAuth dùng chung cho giai đoạn bắt đầu redirect và callback Google. */
    public GoogleRedirectFilterPostProcessor(AuthErrorHandler errors) {
        this.errors = errors;
    }

    /**
     * Gắn googleFailure vào filter tạo OAuth authorization request. Lỗi xảy ra trước khi đi tới
     * Google cũng được map về ValidateCode, cùng cách xử lý với lỗi callback.
     */
    @Override
    public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(O filter) {
        filter.setAuthenticationFailureHandler(errors::googleFailure);
        return filter;
    }
}
