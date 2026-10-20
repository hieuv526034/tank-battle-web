package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.entity.BaseEntity;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;

/** Metadata và kết quả chính thức; state mô phỏng ở match_snapshots. */
@Entity
@Table(name = "matches")
public class MatchEntity extends BaseEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "recovery_deadline_at")
    private Instant recoveryDeadlineAt;

    @Column(name = "winner_user_id")
    private Long winnerUserId;

    @Column(name = "end_reason", length = 60)
    private String endReason;

    @Column(name = "rules_version", nullable = false)
    private int rulesVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 16)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty;

    @Column(name = "stage_number", nullable = false)
    private int stage;

    public String getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getStatus() {
        return status;
    }
}
