package vn.edu.utc.tankbattle.auth.service;

import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.auth.config.BootstrapAdminProperties;
import vn.edu.utc.tankbattle.auth.model.BootstrapAdminAccount;
import vn.edu.utc.tankbattle.auth.repository.BootstrapAdminRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidationRules;

/**
 * Đảm bảo tài khoản quản trị đầu tiên sau Flyway trong DB ứng dụng. Không tự nâng USER lên ADMIN
 * hoặc mở lại admin đã bị khóa; test dùng DB và cấu hình riêng.
 */
@Component
@ConditionalOnProperty(prefix = "tank.bootstrap-admin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final BootstrapAdminRepository repository;
    private final PasswordEncoder passwords;
    private final BootstrapAdminProperties properties;

    /**
     * Nhận repository, encoder PBKDF2 chung và cấu hình; không dùng thông tin xác thực trong log.
     */
    public BootstrapAdminInitializer(
            BootstrapAdminRepository repository,
            PasswordEncoder passwords,
            BootstrapAdminProperties properties) {
        this.repository = repository;
        this.passwords = passwords;
        this.properties = properties;
    }

    /**
     * Spring gọi qua proxy transaction sau khi schema sẵn sàng. Khóa guard rồi kiểm tra username:
     * admin đã có được giữ nguyên; username bị chiếm/khóa làm startup thất bại để người vận hành xử
     * lý, không âm thầm nâng quyền. Chỉ khi cần tạo mới mới yêu cầu mật khẩu 12–64 ký tự.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String username = TextNormalizer.username(properties.username());
        String displayName = TextNormalizer.name(properties.displayName());
        if (!ValidationRules.valid(TextKind.USERNAME, username)
                || !ValidationRules.valid(TextKind.DISPLAY_NAME, displayName)) {
            throw new IllegalStateException(
                    "Cấu hình username/display-name của bootstrap admin không hợp lệ.");
        }
        repository.lockBootstrap();
        Optional<BootstrapAdminAccount> existing = repository.find(username);
        if (existing.isPresent()) {
            BootstrapAdminAccount account = existing.get();
            if (!"ADMIN".equals(account.role()) || !account.enabled() || !account.passwordLogin()) {
                throw new IllegalStateException(
                        "Username bootstrap đã tồn tại nhưng không phải admin đăng nhập được; kiểm tra cấu hình và dữ liệu quản trị.");
            }
            return;
        }
        if (!ValidationRules.valid(TextKind.PASSWORD, properties.password())) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD cần 12–64 ký tự khi tạo admin lần đầu.");
        }
        repository.insert(username, displayName, passwords.encode(properties.password()));
    }
}
