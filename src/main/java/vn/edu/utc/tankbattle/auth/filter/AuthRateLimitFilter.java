package vn.edu.utc.tankbattle.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import vn.edu.utc.tankbattle.auth.model.RateWindow;
import vn.edu.utc.tankbattle.auth.security.SecurityErrorWriter;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/**
 * Giới hạn cơ bản theo remote address cho một instance, không khóa tài khoản vĩnh viễn. Reverse
 * proxy production cần cấu hình forwarded headers tin cậy để địa chỉ đúng.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_BUCKETS = 10_000;
    private final ConcurrentHashMap<String, RateWindow> windows = new ConcurrentHashMap<>();
    private final SecurityErrorWriter errors;

    /** Nhận SecurityErrorWriter để trả lỗi rate limit cùng locale/envelope với lỗi auth. */
    public AuthRateLimitFilter(SecurityErrorWriter errors) {
        this.errors = errors;
    }

    /**
     * Chọn các POST đăng nhập/đăng ký/trao đổi token và nhóm /api/auth để áp rate limit. Các
     * request khác đi thẳng; đường dẫn được lấy tương đối ứng dụng để vẫn đúng khi có context path.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !"POST".equals(request.getMethod())
                || !(path.equals("/login")
                        || path.equals("/register")
                        || path.equals("/auth/token")
                        || path.startsWith("/api/auth/"));
    }

    /**
     * Đếm request theo remote address + endpoint trong cửa sổ 60 giây bằng
     * ConcurrentHashMap.compute. Giới hạn 120 cho refresh và 20 cho endpoint auth khác; trần số
     * bucket bảo vệ RAM của một instance. Quá hạn mức thì ghi 429, còn hợp lệ mới gọi chain tiếp
     * theo.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String address = request.getRemoteAddr();
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        String key = address + ":" + path;
        if (windows.size() >= MAX_BUCKETS && !windows.containsKey(key)) {
            reject(request, response);
            return;
        }
        RateWindow window =
                windows.compute(
                        key,
                        (ignored, previous) -> {
                            if (previous == null || now - previous.startedAt() > WINDOW_MS) {
                                return new RateWindow(now, 1);
                            }
                            return new RateWindow(previous.startedAt(), previous.count() + 1);
                        });
        int limit = path.endsWith("refresh") ? 120 : 20;
        if (window.count() > limit) {
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Ghi SYS_TOO_MANY_REQUESTS và Retry-After 60 để client biết cần chờ trước khi thử lại. */
    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader("Retry-After", "60");
        errors.write(request, response, ValidateCode.SYS_TOO_MANY_REQUESTS);
    }

    /** Mỗi phút xóa bucket đã hết cửa sổ để địa chỉ không còn gọi không chiếm RAM lâu dài. */
    @Scheduled(fixedDelay = WINDOW_MS)
    public void removeExpiredWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > WINDOW_MS);
    }
}
