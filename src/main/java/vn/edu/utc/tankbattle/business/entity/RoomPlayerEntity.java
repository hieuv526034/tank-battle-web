package vn.edu.utc.tankbattle.business.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edu.utc.tankbattle.common.base.entity.BaseEntity;

/** Membership hiện tại; lịch sử trận nằm riêng trong match_players. */
@Entity
@Table(
        name = "room_players",
        indexes = @Index(name = "idx_room_players_room", columnList = "room_id"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "room_players_room_id_seat_key",
                        columnNames = {"room_id", "seat"}))
public class RoomPlayerEntity extends BaseEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private int seat;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public Long getUserId() {
        return userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public int getSeat() {
        return seat;
    }

    public boolean isReady() {
        return ready;
    }
}
