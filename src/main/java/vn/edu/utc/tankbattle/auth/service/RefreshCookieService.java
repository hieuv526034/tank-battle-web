package vn.edu.utc.tankbattle.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.auth.config.AuthProperties;
import vn.edu.utc.tankbattle.auth.model.TokenGrant;
import vn.edu.utc.tankbattle.common.constant.AppConstants;

/** Cookie refresh không cho JavaScript đọc và chỉ gửi tới nhóm endpoint auth. */
@Component
public class RefreshCookieService {
    private final AuthProperties properties;

    /** Nhận cấu hình secureCookie để cùng code chạy local HTTP và production HTTPS theo profile. */
    public RefreshCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * Tạo giá trị Set-Cookie cho refresh token mới, maxAge theo hạn phiên còn lại. Caller dùng
     * header này ở login/refresh/trao đổi token; refresh thô không đưa vào JSON response.
     */
    public String write(TokenGrant grant) {
        Duration remaining = Duration.between(Instant.now(), grant.refreshExpiresAt());
        return build(grant.refreshToken(), remaining.isNegative() ? Duration.ZERO : remaining);
    }

    /**
     * Tạo cookie cùng tên/path với maxAge=0 để trình duyệt xóa refresh hiện tại. Dùng khi logout
     * hoặc refresh không hợp lệ, đồng thời với việc thu hồi/xử lý phiên ở service.
     */
    public String clear() {
        return build("", Duration.ZERO);
    }

    /**
     * Tập trung thuộc tính cookie: HttpOnly, Secure theo môi trường, SameSite Strict và path
     * /api/auth. Cùng bộ thuộc tính được dùng khi ghi và xóa để trình duyệt tác động đúng cookie.
     */
    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(AppConstants.REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Strict")
                .path(AppConstants.REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
