package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Khóa ghép chống ghi hai kết quả cho cùng người trong cùng trận. */
@Embeddable
public class MatchPlayerId implements Serializable {
    @Column(name = "match_id", length = 36)
    private String matchId;

    @Column(name = "user_id")
    private Long userId;

    public MatchPlayerId() {}

    public MatchPlayerId(String matchId, Long userId) {
        this.matchId = matchId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MatchPlayerId other
                && Objects.equals(matchId, other.matchId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, userId);
    }
}
