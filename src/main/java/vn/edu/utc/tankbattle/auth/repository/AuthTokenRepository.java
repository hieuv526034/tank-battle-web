package vn.edu.utc.tankbattle.auth.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.auth.model.AuthSession;
import vn.edu.utc.tankbattle.auth.model.RefreshTokenRow;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

/** SQL riêng cho lifecycle token: khóa một phiên, không lặp find/save theo danh sách token. */
@Repository
public class AuthTokenRepository {
    private final JdbcTemplate db;

    public AuthTokenRepository(JdbcTemplate db) {
        this.db = db;
    }

    public void createSession(String id, long userId, Instant expiresAt) {
        db.update(
                "INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,?)",
                id,
                userId,
                JdbcTimeUtils.toDatabase(expiresAt));
    }

    public void createRefresh(String id, String sessionId, String hash, Instant expiresAt) {
        db.update(
                "INSERT INTO auth_refresh_tokens(id,session_id,token_hash,expires_at) VALUES(?,?,?,?)",
                id,
                sessionId,
                hash,
                JdbcTimeUtils.toDatabase(expiresAt));
    }

    public Optional<RefreshTokenRow> findRefresh(String id) {
        return db
                .query(
                        "SELECT * FROM auth_refresh_tokens WHERE id=?",
                        (row, number) ->
                                new RefreshTokenRow(
                                        row.getString("id"),
                                        row.getString("session_id"),
                                        row.getString("token_hash"),
                                        JdbcTimeUtils.readInstant(row, "expires_at"),
                                        JdbcTimeUtils.readInstant(row, "consumed_at")),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<AuthSession> lockSession(String id) {
        return db
                .query(
                        """
                    SELECT s.*,u.username,u.role,u.enabled
                    FROM auth_sessions s JOIN users u ON u.id=s.user_id
                    WHERE s.id=? FOR UPDATE
                    """,
                        (row, number) ->
                                new AuthSession(
                                        row.getString("id"),
                                        row.getLong("user_id"),
                                        row.getString("username"),
                                        row.getString("role"),
                                        row.getBoolean("enabled"),
                                        JdbcTimeUtils.readInstant(row, "expires_at"),
                                        JdbcTimeUtils.readInstant(row, "revoked_at")),
                        id)
                .stream()
                .findFirst();
    }

    public void consume(String tokenId) {
        db.update(
                "UPDATE auth_refresh_tokens SET consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND consumed_at IS NULL",
                tokenId);
    }

    public void revokeSession(String id) {
        db.update(
                "UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND revoked_at IS NULL",
                id);
    }

    public void revokeAll(long userId) {
        db.update(
                "UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL",
                userId);
    }

    /** Một lookup theo PK để access token bị thu hồi có hiệu lực ngay, không cache sai quyền. */
    public boolean active(String sessionId, long userId) {
        Integer count =
                db.queryForObject(
                        """
                    SELECT COUNT(*) FROM auth_sessions s JOIN users u ON u.id=s.user_id
                    WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL
                      AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE
                    """,
                        Integer.class,
                        sessionId,
                        userId);
        return count != null && count == 1;
    }

    /** Dọn theo lô, giữ token đã dùng cho tới lúc hết hạn phiên để phát hiện replay. */
    public void deleteExpired(Instant before) {
        db.update(
                "DELETE FROM auth_refresh_tokens WHERE session_id IN (SELECT id FROM auth_sessions WHERE expires_at<?)",
                JdbcTimeUtils.toDatabase(before));
        db.update("DELETE FROM auth_sessions WHERE expires_at<?", JdbcTimeUtils.toDatabase(before));
    }
}
