package vn.edu.utc.tankbattle.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.auth.config.LocalDemoProperties;
import vn.edu.utc.tankbattle.auth.model.LocalDemoAccount;
import vn.edu.utc.tankbattle.auth.repository.LocalDemoRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.validation.ValidationRules;

/**
 * Seed tùy chọn, chỉ local và tuyệt đối không prod. Thêm tài khoản còn thiếu; không xóa dữ liệu,
 * reset mật khẩu hay ghi đè hồ sơ mỗi lần khởi động.
 */
@Component
@Profile("local & !prod")
@ConditionalOnProperty(prefix = "tank.local-demo", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalDemoProperties.class)
public class LocalDemoSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);
    private final LocalDemoRepository repository;
    private final PasswordEncoder passwords;
    private final LocalDemoProperties properties;

    /** Nhận kho seed theo lô, PasswordEncoder và mật khẩu demo từ cấu hình local. */
    public LocalDemoSeeder(
            LocalDemoRepository repository,
            PasswordEncoder passwords,
            LocalDemoProperties properties) {
        this.repository = repository;
        this.passwords = passwords;
        this.properties = properties;
    }

    /**
     * Chạy sau startup khi đồng thời thỏa profile local, không prod và bật local-demo.enabled. Kiểm
     * tra mật khẩu mẫu, lấy danh sách user hiện có bằng một query, băm và batch insert những tài
     * khoản còn thiếu. Toàn bộ seed trong một transaction; lần khởi động sau giữ tên/hash người
     * dùng đã sửa. Log chỉ ghi số lượng tài khoản.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!ValidationRules.valid(TextKind.PASSWORD, properties.password())) {
            throw new IllegalArgumentException("LOCAL_DEMO_PASSWORD phải có từ 12 đến 64 ký tự.");
        }
        List<String> usernames =
                IntStream.rangeClosed(1, 4).mapToObj(number -> "utc_demo" + number).toList();
        Set<String> existing = repository.existingUsernames(usernames);
        List<LocalDemoAccount> missing =
                usernames.stream()
                        .filter(username -> !existing.contains(username))
                        .map(
                                username ->
                                        new LocalDemoAccount(
                                                username,
                                                "Người chơi " + username,
                                                passwords.encode(properties.password())))
                        .toList();
        repository.insertAll(missing);
        log.info("Local demo: đã tạo {} tài khoản còn thiếu.", missing.size());
    }
}
