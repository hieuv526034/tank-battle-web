package vn.edu.utc.tankbattle.business.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.business.dto.response.MatchResponse;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;
import vn.edu.utc.tankbattle.business.dto.response.ResultResponse;
import vn.edu.utc.tankbattle.business.mapper.MatchMapper;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.business.model.MatchPlayerResult;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

/** SQL trận/checkpoint/kết quả. Caller service giữ transaction và thứ tự khóa room → match. */
@Repository
public class MatchRepository {
    private static final String SELECT =
            "SELECT m.*,r.code,r.name,s.payload FROM matches m JOIN rooms r ON r.id=m.room_id LEFT JOIN match_snapshots s ON s.match_id=m.id ";
    private static final String METADATA =
            "SELECT m.*,r.code,r.name,NULL AS payload FROM matches m JOIN rooms r ON r.id=m.room_id ";
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;

    public MatchRepository(JdbcTemplate db) {
        this.db = db;
        this.named = new NamedParameterJdbcTemplate(db);
    }

    public Optional<MatchResponse> find(String id) {
        return query(SELECT + "WHERE m.id=?", id).stream().findFirst();
    }

    public Optional<MatchResponse> metadata(String id) {
        return query(METADATA + "WHERE m.id=?", id).stream().findFirst();
    }

    private List<MatchResponse> query(String sql, Object... arguments) {
        return db.query(sql, (row, index) -> MatchMapper.metadata(row), arguments);
    }

    public List<MatchResponse> unfinished() {
        return query(SELECT + "WHERE m.status IN ('RUNNING','RECOVERING') ORDER BY m.started_at");
    }

    public Map<String, List<String>> allForfeits(List<String> matchIds) {
        if (matchIds.isEmpty()) return Map.of();
        List<Map<String, Object>> rows =
                named.queryForList(
                        "SELECT match_id,user_id FROM match_players WHERE match_id IN (:ids) AND forfeited_at IS NOT NULL",
                        Map.of("ids", matchIds));
        Map<String, List<String>> grouped = new HashMap<>();
        for (Map<String, Object> row : rows) {
            grouped.computeIfAbsent((String) row.get("match_id"), ignored -> new ArrayList<>())
                    .add(row.get("user_id").toString());
        }
        return grouped;
    }

    public List<String> forfeits(String id) {
        return db.query(
                "SELECT user_id FROM match_players WHERE match_id=? AND forfeited_at IS NOT NULL",
                (row, index) -> Long.toString(row.getLong(1)),
                id);
    }

    public void markForfeited(long userId, long roomId) {
        db.update(
                "UPDATE match_players SET forfeited_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND match_id IN (SELECT id FROM matches WHERE room_id=? AND status IN ('RUNNING','RECOVERING')) AND forfeited_at IS NULL",
                userId,
                roomId);
    }

    public void prepareRecovery(List<MatchResponse> matches, Instant fallback) {
        db.batchUpdate(
                "UPDATE matches SET status='RECOVERING',recovery_deadline_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('RUNNING','RECOVERING')",
                matches.stream()
                        .map(
                                match ->
                                        new Object[] {
                                            JdbcTimeUtils.toDatabase(
                                                    match.recoveryDeadline() == null
                                                            ? fallback
                                                            : match.recoveryDeadline()),
                                            match.id()
                                        })
                        .toList());
        db.batchUpdate(
                "UPDATE rooms SET status='RECOVERING',updated_at=CURRENT_TIMESTAMP WHERE id=?",
                matches.stream().map(match -> new Object[] {match.roomId()}).toList());
        db.batchUpdate(
                "UPDATE room_players SET ready=FALSE,updated_at=CURRENT_TIMESTAMP WHERE room_id=?",
                matches.stream().map(match -> new Object[] {match.roomId()}).toList());
    }

    public List<MatchResponse> history(long userId, int limit, int offset) {
        return query(
                METADATA
                        + "JOIN match_players p ON p.match_id=m.id WHERE p.user_id=? ORDER BY m.started_at DESC,m.id LIMIT ? OFFSET ?",
                userId,
                limit,
                offset);
    }

    public boolean participates(String id, long userId) {
        return db.queryForObject(
                        "SELECT COUNT(*) FROM match_players WHERE match_id=? AND user_id=?",
                        Integer.class,
                        id,
                        userId)
                > 0;
    }

    public List<ResultResponse> results(String id) {
        return db.query(
                "SELECT p.user_id,u.display_name,p.outcome,p.final_hp,p.kills,p.score FROM match_players p JOIN users u ON u.id=p.user_id WHERE match_id=? ORDER BY p.seat",
                (row, index) -> MatchMapper.result(row),
                id);
    }

    public void insert(GameState state, long roomId) {
        db.update(
                "INSERT INTO matches(id,room_id,status,rules_version,game_mode,difficulty,stage_number) VALUES(?,?,'RUNNING',?,?,?,?)",
                state.matchId,
                roomId,
                state.rules.version,
                state.mode.name(),
                state.difficulty.name(),
                state.stage == null ? 1 : state.stage.number());
    }

    public void insertPlayers(String id, List<MemberResponse> members) {
        db.batchUpdate(
                "INSERT INTO match_players(match_id,user_id,seat) VALUES(?,?,?)",
                members.stream()
                        .map(member -> new Object[] {id, member.userId(), member.seat()})
                        .toList());
    }

    public void insertSnapshot(String id, int schemaVersion, long tick, String payload) {
        db.update(
                "INSERT INTO match_snapshots(match_id,schema_version,tick,payload) VALUES(?,?,?,?)",
                id,
                schemaVersion,
                tick,
                payload);
    }

    public String lockStatus(String id) {
        return db.queryForObject(
                "SELECT status FROM matches WHERE id=? FOR UPDATE", String.class, id);
    }

    public void checkpoint(String id, long tick, String payload) {
        db.update(
                "UPDATE match_snapshots SET schema_version=2,tick=?,payload=?,saved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE match_id=? AND tick<=? AND EXISTS (SELECT 1 FROM matches WHERE id=? AND status='RUNNING')",
                tick,
                payload,
                id,
                tick,
                id);
    }

    public void saveFinalSnapshot(String id, long tick, String payload) {
        db.update(
                "UPDATE match_snapshots SET schema_version=2,tick=?,payload=?,saved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE match_id=?",
                tick,
                payload,
                id);
    }

    public void updateResults(String id, List<MatchPlayerResult> results) {
        db.batchUpdate(
                "UPDATE match_players SET outcome=?,final_hp=?,kills=?,score=?,updated_at=CURRENT_TIMESTAMP WHERE match_id=? AND user_id=?",
                results.stream()
                        .map(
                                result ->
                                        new Object[] {
                                            result.outcome(),
                                            result.hp(),
                                            result.kills(),
                                            result.score(),
                                            id,
                                            result.userId()
                                        })
                        .toList());
    }

    public void finish(String id, Long winner, String reason) {
        db.update(
                "UPDATE matches SET status='FINISHED',ended_at=CURRENT_TIMESTAMP,winner_user_id=?,end_reason=?,recovery_deadline_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                winner,
                reason,
                id);
    }

    public void abort(String id, String reason) {
        db.update(
                "UPDATE matches SET status='ABORTED',ended_at=CURRENT_TIMESTAMP,end_reason=?,recovery_deadline_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                reason,
                id);
    }

    public void recovering(String id, Instant deadline) {
        db.update(
                "UPDATE matches SET status='RECOVERING',recovery_deadline_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('RUNNING','RECOVERING')",
                JdbcTimeUtils.toDatabase(deadline),
                id);
    }

    public int resume(String id) {
        return db.update(
                "UPDATE matches SET status='RUNNING',recovery_deadline_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RECOVERING' AND recovery_deadline_at>CURRENT_TIMESTAMP",
                id);
    }

    public void saveForfeits(String id, List<Long> userIds) {
        if (!userIds.isEmpty())
            db.batchUpdate(
                    "UPDATE match_players SET forfeited_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE match_id=? AND user_id=? AND forfeited_at IS NULL",
                    userIds.stream().map(userId -> new Object[] {id, userId}).toList());
    }

    /** Một query kiểm tra toàn roster, không kiểm tra từng user trong vòng lặp. */
    public boolean stageUnlocked(List<Long> users, String difficulty, int stage) {
        if (stage == 1) return true;
        Integer count =
                named.queryForObject(
                        "SELECT COUNT(DISTINCT p.user_id) FROM match_players p JOIN matches m ON m.id=p.match_id WHERE p.user_id IN (:users) AND m.game_mode='CAMPAIGN' AND m.status='FINISHED' AND m.difficulty=:difficulty AND m.stage_number=:previous AND p.outcome='WIN'",
                        Map.of("users", users, "difficulty", difficulty, "previous", stage - 1),
                        Integer.class);
        return count != null && count == users.size();
    }
}
