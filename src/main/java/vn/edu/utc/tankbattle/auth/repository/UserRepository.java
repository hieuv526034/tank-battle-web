package vn.edu.utc.tankbattle.auth.repository;

import java.util.Optional;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.common.base.repository.BaseRepository;

/**
 * Đọc/tạo tài khoản; không cung cấp câu UPDATE hồ sơ cho người chơi. Unique username được database
 * bảo vệ khi đăng ký đồng thời.
 */
public interface UserRepository extends BaseRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}
