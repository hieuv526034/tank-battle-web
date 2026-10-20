package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.entity.BaseEntity;

/** Kết quả lịch sử không mất khi người chơi rời phòng. */
@Entity
@Table(
        name = "match_players",
        indexes = @Index(name = "idx_match_players_user", columnList = "user_id"))
public class MatchPlayerEntity extends BaseEntity {
    @EmbeddedId private MatchPlayerId id;

    @Column(nullable = false)
    private int seat;

    @Column(length = 8)
    private String outcome;

    @Column(name = "final_hp")
    private Integer finalHp;

    @Column(name = "forfeited_at")
    private Instant forfeitedAt;

    @Column(nullable = false)
    private int kills;

    @Column(nullable = false)
    private int score;

    public MatchPlayerId getId() {
        return id;
    }

    public String getOutcome() {
        return outcome;
    }
}
