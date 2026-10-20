package vn.edu.utc.tankbattle.auth.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.AuthProvider;

/**
 * SQL liên kết nhà cung cấp tách khỏi service; unique(provider,subject) bảo vệ callback đồng thời.
 */
@Repository
public class ExternalIdentityRepository {
    private final JdbcTemplate db;

    public ExternalIdentityRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Long> userId(String subject) {
        return db
                .query(
                        "SELECT user_id FROM external_identities WHERE provider=? AND subject=?",
                        (row, index) -> row.getLong(1),
                        AuthProvider.GOOGLE.name(),
                        subject)
                .stream()
                .findFirst();
    }

    public void insert(long userId, String subject) {
        db.update(
                "INSERT INTO external_identities(user_id,provider,subject) VALUES(?,?,?)",
                userId,
                AuthProvider.GOOGLE.name(),
                subject);
    }
}
