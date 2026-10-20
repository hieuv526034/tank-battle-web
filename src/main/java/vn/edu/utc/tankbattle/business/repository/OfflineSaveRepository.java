package vn.edu.utc.tankbattle.business.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.business.model.OfflineSaveRow;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

@Repository
public class OfflineSaveRepository {
    private final JdbcTemplate db;

    public OfflineSaveRepository(JdbcTemplate db) {
        this.db = db;
    }

    public List<OfflineSaveRow> list(long userId) {
        return db.query(
                "SELECT slot,revision,updated_at FROM offline_saves WHERE user_id=? AND deleted=FALSE ORDER BY slot",
                (row, index) ->
                        new OfflineSaveRow(
                                row.getInt("slot"),
                                row.getLong("revision"),
                                null,
                                JdbcTimeUtils.readInstant(row, "updated_at"),
                                false),
                userId);
    }

    public Optional<OfflineSaveRow> find(long userId, int slot) {
        return db
                .query(
                        "SELECT slot,revision,payload,updated_at,deleted FROM offline_saves WHERE user_id=? AND slot=?",
                        (row, index) ->
                                new OfflineSaveRow(
                                        row.getInt("slot"),
                                        row.getLong("revision"),
                                        row.getString("payload"),
                                        JdbcTimeUtils.readInstant(row, "updated_at"),
                                        row.getBoolean("deleted")),
                        userId,
                        slot)
                .stream()
                .findFirst();
    }

    public void insert(long userId, int slot, String payload) {
        db.update(
                "INSERT INTO offline_saves(user_id,slot,revision,payload) VALUES(?,?,1,?)",
                userId,
                slot,
                payload);
    }

    public int update(long userId, int slot, long expected, String payload) {
        return db.update(
                "UPDATE offline_saves SET revision=revision+1,payload=?,deleted=FALSE,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND slot=? AND revision=?",
                payload,
                userId,
                slot,
                expected);
    }

    public int delete(long userId, int slot, long expected) {
        return db.update(
                "UPDATE offline_saves SET revision=revision+1,payload='{}',deleted=TRUE,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND slot=? AND revision=? AND deleted=FALSE",
                userId,
                slot,
                expected);
    }
}
