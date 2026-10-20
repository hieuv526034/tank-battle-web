package vn.edu.utc.tankbattle.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.common.base.repository.BaseRepository;

/**
 * CRUD tài khoản và cập nhật hồ sơ; unique username được database bảo vệ khi có request đồng thời.
 */
public interface UserRepository extends BaseRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    @Modifying
    @Query(
            value = "UPDATE users SET display_name=:name,updated_at=CURRENT_TIMESTAMP WHERE id=:id",
            nativeQuery = true)
    int rename(long id, String name);
}
