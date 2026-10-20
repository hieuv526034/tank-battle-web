package vn.edu.utc.tankbattle.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import vn.edu.utc.tankbattle.common.base.entity.BaseIdentityEntity;
import vn.edu.utc.tankbattle.common.constant.AppConstants.UserRole;

/** Tài khoản; passwordHash chỉ dùng trong tầng xác thực, không map vào response. */
@Entity
@Table(name = "users")
public class UserEntity extends BaseIdentityEntity {
    @Column(nullable = false, unique = true, length = 24)
    private String username;

    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    public boolean isEnabled() {
        return enabled;
    }

    public UserRole getRole() {
        return role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        displayName = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        passwordHash = value;
    }
}
