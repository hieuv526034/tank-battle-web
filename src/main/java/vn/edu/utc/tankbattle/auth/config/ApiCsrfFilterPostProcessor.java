package vn.edu.utc.tankbattle.auth.config;

import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Resource Server mặc định bỏ CSRF cho Bearer. Gắn matcher cuối cùng lên filter để các POST auth
 * dùng cookie vẫn bắt buộc CSRF, kể cả khi có Authorization.
 */
@Component
public class ApiCsrfFilterPostProcessor implements ObjectPostProcessor<CsrfFilter> {
    private final RequestMatcher authEndpoints =
            PathPatternRequestMatcher.withDefaults().matcher("/api/auth/**");

    /**
     * Gắn lại điều kiện CSRF vào CsrfFilter sau khi Resource Server hoàn tất cấu hình.
     * GET/HEAD/OPTIONS theo matcher mặc định không cần CSRF. Request thay đổi dữ liệu thuộc
     * /api/auth/** luôn cần CSRF vì dùng cookie. Ngoài nhóm đó, Bearer thuần được miễn CSRF;
     * request không có Bearer vẫn theo cơ chế CSRF của Spring.
     *
     * @param filter filter thật do HttpSecurity tạo
     * @return chính filter đã được điều chỉnh matcher
     */
    @Override
    public <O extends CsrfFilter> O postProcess(O filter) {
        filter.setRequireCsrfProtectionMatcher(
                request -> {
                    if (!CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) {
                        return false;
                    }
                    String authorization = request.getHeader("Authorization");
                    boolean bearer = authorization != null && authorization.startsWith("Bearer ");
                    return authEndpoints.matches(request) || !bearer;
                });
        return filter;
    }
}
