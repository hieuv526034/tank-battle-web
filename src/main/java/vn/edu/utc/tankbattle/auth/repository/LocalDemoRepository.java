package vn.edu.utc.tankbattle.auth.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.auth.model.LocalDemoAccount;

/** Một query tìm username, một batch insert; không find/save từng tài khoản mẫu. */
@Repository
@Profile("local & !prod")
public class LocalDemoRepository {
    private final JdbcTemplate db;

    public LocalDemoRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Set<String> existingUsernames(List<String> usernames) {
        if (usernames.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(
                new NamedParameterJdbcTemplate(db)
                        .query(
                                "SELECT username FROM users WHERE username IN (:names)",
                                Map.of("names", usernames),
                                (row, index) -> row.getString("username")));
    }

    public void insertAll(List<LocalDemoAccount> accounts) {
        if (accounts.isEmpty()) {
            return;
        }
        db.batchUpdate(
                "INSERT INTO users(username,display_name,password_hash) VALUES(?,?,?)",
                accounts,
                50,
                (statement, account) -> {
                    statement.setString(1, account.username());
                    statement.setString(2, account.displayName());
                    statement.setString(3, account.passwordHash());
                });
    }
}
