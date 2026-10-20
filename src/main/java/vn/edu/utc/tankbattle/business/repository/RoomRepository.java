package vn.edu.utc.tankbattle.business.repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.mapper.RoomMapper;
import vn.edu.utc.tankbattle.business.model.RoomMemberRow;

/** Chỉ đọc/ghi SQL; service quyết định transaction và điều kiện nghiệp vụ. */
@Repository
public class RoomRepository {
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;

    public RoomRepository(JdbcTemplate db) {
        this.db = db;
        this.named = new NamedParameterJdbcTemplate(db);
    }

    public Optional<RoomResponse> byCode(String code, boolean lock) {
        return find("SELECT * FROM rooms WHERE code=?" + (lock ? " FOR UPDATE" : ""), code);
    }

    public Optional<RoomResponse> byId(long id) {
        return find("SELECT * FROM rooms WHERE id=?", id);
    }

    private Optional<RoomResponse> find(String sql, Object key) {
        List<RoomResponse> headers = db.query(sql, (row, index) -> RoomMapper.header(row), key);
        if (headers.isEmpty()) return Optional.empty();
        RoomResponse room = headers.getFirst();
        List<MemberResponse> members =
                db.query(
                        "SELECT p.user_id,u.display_name,p.seat,p.ready FROM room_players p JOIN users u ON u.id=p.user_id WHERE room_id=? ORDER BY seat",
                        (row, index) -> RoomMapper.member(row),
                        room.id());
        return Optional.of(RoomMapper.withMembers(room, members));
    }

    /** Hai query cho cả danh sách phòng, không tải member theo từng phòng. */
    public List<RoomResponse> list() {
        List<RoomResponse> headers =
                db.query(
                        "SELECT * FROM rooms WHERE status<>'CLOSED' ORDER BY created_at DESC LIMIT 40",
                        (row, index) -> RoomMapper.header(row));
        if (headers.isEmpty()) return headers;
        List<RoomMemberRow> rows =
                named.query(
                        "SELECT p.room_id,p.user_id,u.display_name,p.seat,p.ready FROM room_players p JOIN users u ON u.id=p.user_id WHERE p.room_id IN (:ids) ORDER BY p.room_id,p.seat",
                        Map.of("ids", headers.stream().map(RoomResponse::id).toList()),
                        (row, index) ->
                                new RoomMemberRow(row.getLong("room_id"), RoomMapper.member(row)));
        Map<Long, List<MemberResponse>> grouped = new HashMap<>();
        rows.forEach(
                row ->
                        grouped.computeIfAbsent(row.roomId(), ignored -> new ArrayList<>())
                                .add(row.member()));
        return headers.stream()
                .map(
                        room ->
                                RoomMapper.withMembers(
                                        room, grouped.getOrDefault(room.id(), List.of())))
                .toList();
    }

    public Optional<Long> currentRoomId(long userId) {
        return db
                .query(
                        "SELECT room_id FROM room_players WHERE user_id=?",
                        (row, index) -> row.getLong(1),
                        userId)
                .stream()
                .findFirst();
    }

    public void lockUser(long userId) {
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, userId);
    }

    public void lockCreationGuard() {
        db.queryForObject(
                "SELECT name FROM app_locks WHERE name='CREATE_ROOM' FOR UPDATE", String.class);
    }

    /** Khóa theo một thứ tự duy nhất khi phục hồi nhiều phòng; không query từng phòng. */
    public void lockRooms(List<Long> ids) {
        if (!ids.isEmpty())
            named.queryForList(
                    "SELECT id FROM rooms WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                    Map.of("ids", ids));
    }

    public int activeCount() {
        return db.queryForObject(
                "SELECT COUNT(*) FROM rooms WHERE status<>'CLOSED'", Integer.class);
    }

    public boolean codeExists(String code) {
        return db.queryForObject("SELECT COUNT(*) FROM rooms WHERE code=?", Integer.class, code)
                > 0;
    }

    public long insert(String code, String name, long userId, int capacity) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO rooms(code,name,host_user_id,capacity,status) VALUES(?,?,?,?,'WAITING')",
                                    new String[] {"id"});
                    statement.setString(1, code);
                    statement.setString(2, name);
                    statement.setLong(3, userId);
                    statement.setInt(4, capacity);
                    return statement;
                },
                key);
        return key.getKey().longValue();
    }

    public void addMember(long userId, long roomId, int seat) {
        db.update(
                "INSERT INTO room_players(user_id,room_id,seat) VALUES(?,?,?)",
                userId,
                roomId,
                seat);
    }

    public void setReady(long userId, boolean ready) {
        db.update(
                "UPDATE room_players SET ready=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                ready,
                userId);
    }

    public void resetReady(long roomId) {
        db.update(
                "UPDATE room_players SET ready=FALSE,updated_at=CURRENT_TIMESTAMP WHERE room_id=?",
                roomId);
    }

    public void removeMember(long userId, long roomId) {
        db.update("DELETE FROM room_players WHERE user_id=? AND room_id=?", userId, roomId);
    }

    public void setStatus(long roomId, String status) {
        db.update(
                "UPDATE rooms SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                status,
                roomId);
    }

    public void reconcile(long roomId) {
        List<Long> remaining =
                db.query(
                        "SELECT user_id FROM room_players WHERE room_id=? ORDER BY joined_at,seat",
                        (row, index) -> row.getLong(1),
                        roomId);
        if (remaining.isEmpty()) {
            setStatus(roomId, "CLOSED");
            return;
        }
        Long host =
                db.queryForObject("SELECT host_user_id FROM rooms WHERE id=?", Long.class, roomId);
        if (!remaining.contains(host))
            db.update(
                    "UPDATE rooms SET host_user_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    remaining.getFirst(),
                    roomId);
        resetReady(roomId);
    }

    /** Khóa room theo ID tăng dần rồi xóa/reset/batch cập nhật chủ phòng. */
    public void evictDisconnected(Set<Long> userIds) {
        if (userIds.isEmpty()) return;
        List<Long> roomIds =
                named.queryForList(
                        "SELECT id FROM rooms WHERE status='WAITING' AND id IN (SELECT room_id FROM room_players WHERE user_id IN (:users)) ORDER BY id FOR UPDATE",
                        Map.of("users", userIds),
                        Long.class);
        if (roomIds.isEmpty()) return;
        named.update(
                "DELETE FROM room_players WHERE user_id IN (:users) AND room_id IN (:rooms)",
                Map.of("users", userIds, "rooms", roomIds));
        named.update(
                "UPDATE room_players SET ready=FALSE,updated_at=CURRENT_TIMESTAMP WHERE room_id IN (:rooms)",
                Map.of("rooms", roomIds));
        db.batchUpdate(
                "UPDATE rooms SET host_user_id=COALESCE((SELECT user_id FROM room_players WHERE room_id=? ORDER BY joined_at,seat LIMIT 1),host_user_id),status=CASE WHEN EXISTS (SELECT 1 FROM room_players WHERE room_id=?) THEN 'WAITING' ELSE 'CLOSED' END,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                roomIds.stream().map(id -> new Object[] {id, id, id}).toList());
    }
}
