package vn.edu.utc.tankbattle.common.base.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Audit dùng chung bằng Instant, biểu diễn một mốc thời gian độc lập múi giờ. Flyway là nguồn
 * schema; JPA chỉ validate, không tự sửa database.
 */
@MappedSuperclass
public abstract class BaseEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
