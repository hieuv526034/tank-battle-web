package vn.edu.utc.tankbattle.auth.service;

import static vn.edu.utc.tankbattle.common.exception.BusinessException.of;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.auth.dto.response.UserResponse;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.mapper.UserMapper;
import vn.edu.utc.tankbattle.auth.repository.UserRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidationRules;

/**
 * Nghiệp vụ tài khoản local: chuẩn hóa, kiểm tra invariant, ánh xạ response và điều phối
 * transaction ghi.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {
    private final UserRepository users;

    /**
     * Nhận UserRepository để quản lý tài khoản; ranh giới transaction đặt ở các phương thức
     * service.
     */
    public AccountService(UserRepository users) {
        this.users = users;
    }

    /**
     * Chuẩn hóa username theo quy tắc chung trước khi tìm tài khoản. Cùng cách chuẩn hóa với
     * request đăng ký giúp khác biệt khoảng trắng/chữ hoa không tạo hai định danh ngoài ý muốn.
     */
    public static String normalize(String username) {
        return TextNormalizer.username(username);
    }

    /**
     * Đọc tài khoản theo username và map sang UserResponse công khai cho chính chủ. Mapper loại
     * password hash và các trường nội bộ trước khi controller serialize response.
     */
    public UserResponse byUsername(String username) {
        return UserMapper.toResponse(credentials(username));
    }

    /**
     * Đọc entity tài khoản cho quá trình xác thực hoặc cấp token. Entity có password hash nên chỉ
     * dùng trong backend; nếu không có tài khoản sẽ ném USER_NOT_FOUND.
     *
     * @param username giá trị được chuẩn hóa trước query
     * @return tài khoản để AuthenticationManager/TokenService kiểm tra
     */
    public UserEntity credentials(String username) {
        return users.findByUsername(normalize(username))
                .orElseThrow(() -> of(ValidateCode.USER_NOT_FOUND));
    }

    /**
     * Tạo tài khoản local trong một transaction sau khi chuẩn hóa và validate username/tên hiển
     * thị. Caller phải băm mật khẩu bằng PasswordEncoder trước khi truyền hash. saveAndFlush đẩy
     * INSERT để phát hiện unique username ngay trong thao tác đăng ký.
     *
     * @param hash password hash đã băm; không phải mật khẩu thô
     * @return ID tài khoản vừa lưu
     */
    @Transactional
    public long create(String username, String name, String hash) {
        username = TextNormalizer.username(username);
        name = TextNormalizer.name(name);
        ValidationRules.require(TextKind.USERNAME, username);
        ValidationRules.require(TextKind.DISPLAY_NAME, name);
        UserEntity entity = new UserEntity();
        entity.setUsername(normalize(username));
        entity.setDisplayName(name.strip());
        entity.setPasswordHash(hash);
        return users.saveAndFlush(entity).getId();
    }
}
