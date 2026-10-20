package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import vn.edu.utc.tankbattle.common.base.entity.BaseEntity;

/** Ánh xạ bảng để Hibernate validate; ghi theo revision bằng JDBC tại OfflineSaveRepository. */
@Entity
@Table(name = "offline_saves")
public class OfflineSaveEntity extends BaseEntity {
    @EmbeddedId private OfflineSaveId id;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean deleted;

    public OfflineSaveId getId() {
        return id;
    }

    public long getRevision() {
        return revision;
    }
}
