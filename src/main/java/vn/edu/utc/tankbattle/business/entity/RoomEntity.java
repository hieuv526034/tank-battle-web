package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import vn.edu.utc.tankbattle.common.base.entity.BaseIdentityEntity;

/** Phòng dùng FK dạng ID, đọc màn hình bằng projection/batch để tránh N+1. */
@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "rooms_code_key", columnNames = "code"))
public class RoomEntity extends BaseIdentityEntity {
    @Column(nullable = false, length = 8)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 16)
    private String status;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getHostUserId() {
        return hostUserId;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getStatus() {
        return status;
    }
}
