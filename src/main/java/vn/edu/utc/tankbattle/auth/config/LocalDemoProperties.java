package vn.edu.utc.tankbattle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nhận cấu hình tank.local-demo cho LocalDemoSeeder. Seeder chỉ chạy khi profile local và cờ
 * enabled được bật; cấu hình này không tự tạo tài khoản hoặc áp dụng cho production.
 *
 * @param password mật khẩu ban đầu của tài khoản demo, được băm trước khi lưu và không ghi log
 */
@ConfigurationProperties(prefix = "tank.local-demo")
public record LocalDemoProperties(String password) {}
