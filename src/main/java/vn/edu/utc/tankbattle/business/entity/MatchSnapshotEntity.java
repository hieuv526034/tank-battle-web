package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.entity.BaseEntity;

/** Một checkpoint mới nhất của mỗi trận; payload có schemaVersion. */
@Entity
@Table(name = "match_snapshots")
public class MatchSnapshotEntity extends BaseEntity {
    @Id
    @Column(name = "match_id", length = 36)
    private String matchId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(nullable = false)
    private long tick;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public String getMatchId() {
        return matchId;
    }

    public String getPayload() {
        return payload;
    }

    public long getTick() {
        return tick;
    }
}
