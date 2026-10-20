package vn.edu.utc.tankbattle.auth.mapper;

import vn.edu.utc.tankbattle.auth.dto.response.UserResponse;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;

/** Chỉ whitelist ba trường public. Không serialize UserEntity vào response. */
public final class UserMapper {
    private UserMapper() {}

    public static UserResponse toResponse(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
