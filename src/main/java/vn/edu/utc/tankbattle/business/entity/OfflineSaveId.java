package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Khóa ghép bảo đảm tối đa một bản lưu/user/slot ở DB. */
@Embeddable
public class OfflineSaveId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    private int slot;

    public OfflineSaveId() {}

    public OfflineSaveId(Long userId, int slot) {
        this.userId = userId;
        this.slot = slot;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof OfflineSaveId id
                        && slot == id.slot
                        && Objects.equals(userId, id.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, slot);
    }
}
