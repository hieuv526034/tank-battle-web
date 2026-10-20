package vn.edu.utc.tankbattle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình tài khoản quản trị đầu tiên; mật khẩu chỉ dùng khi chưa có tài khoản và được băm trước
 * khi lưu. Không dùng record.toString mặc định vì chứa thông tin xác thực.
 */
@ConfigurationProperties(prefix = "tank.bootstrap-admin")
public record BootstrapAdminProperties(String username, String displayName, String password) {
    /** Không để thông tin xác thực xuất hiện khi đối tượng cấu hình bị ghi log. */
    @Override
    public String toString() {
        return "BootstrapAdminProperties[credentials redacted]";
    }
}
