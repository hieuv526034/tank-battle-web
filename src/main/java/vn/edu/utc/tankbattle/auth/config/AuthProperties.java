package vn.edu.utc.tankbattle.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gom các thuộc tính tank.auth từ application.yml/biến môi trường thành một cấu hình có kiểu.
 * Spring tạo bean này khi khởi động; JwtConfiguration, TokenService và RefreshCookieService dùng
 * chung để không tự đặt thời hạn hoặc cách ký token khác nhau.
 *
 * @param issuer định danh hệ thống phát JWT, phải khớp khi decoder kiểm tra token
 * @param audience định danh API được phép nhận JWT
 * @param accessTtl thời gian sống access token, tối đa 15 phút
 * @param refreshTtl thời gian sống phiên refresh, dài hơn access và tối đa 30 ngày
 * @param keyId mã khóa đưa vào JWT header để nhận diện cặp RSA đang dùng
 * @param privateKey đường dẫn Spring Resource tới private PEM, ví dụ file:./secrets/private.pem
 * @param publicKey đường dẫn Spring Resource tới public PEM tương ứng
 * @param ephemeralKeys cho phép sinh cặp khóa tạm khi kiểm thử; prod luôn yêu cầu PEM ổn định
 * @param secureCookie cookie refresh chỉ được gửi qua HTTPS khi giá trị này là true
 */
@ConfigurationProperties(prefix = "tank.auth")
public record AuthProperties(
        String issuer,
        String audience,
        Duration accessTtl,
        Duration refreshTtl,
        String keyId,
        String privateKey,
        String publicKey,
        boolean ephemeralKeys,
        boolean secureCookie) {

    /**
     * Kiểm tra invariant ngay khi Spring bind cấu hình tank.auth. Access TTL phải dương và tối đa
     * 15 phút; refresh TTL dài hơn access và tối đa 30 ngày; issuer/audience bắt buộc có. Ném lỗi
     * cấu hình lúc startup giúp phát hiện sai môi trường trước khi nhận request đăng nhập.
     */
    public AuthProperties {
        if (accessTtl == null
                || accessTtl.isNegative()
                || accessTtl.isZero()
                || accessTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("Access token cần thời hạn từ 1 giây đến 15 phút.");
        }
        if (refreshTtl == null
                || refreshTtl.compareTo(accessTtl) <= 0
                || refreshTtl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException(
                    "Refresh token cần dài hơn access token, tối đa 30 ngày.");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Phải cấu hình issuer và audience.");
        }
    }
}
