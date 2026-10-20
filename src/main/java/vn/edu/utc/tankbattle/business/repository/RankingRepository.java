package vn.edu.utc.tankbattle.business.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.utc.tankbattle.business.dto.response.CampaignRankResponse;
import vn.edu.utc.tankbattle.business.dto.response.RankResponse;

/** Điểm luôn suy ra từ kết quả FINISHED; retry/restore không tự cộng thêm bộ đếm. */
@Repository
public class RankingRepository {
    private final JdbcTemplate db;

    public RankingRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static final String RANKS =
            """
                    WITH totals AS (
                      SELECT p.user_id,u.display_name,COUNT(*) AS played,
                        SUM(CASE WHEN p.outcome='WIN' THEN 1 ELSE 0 END) AS wins,
                        SUM(CASE WHEN p.outcome='LOSS' THEN 1 ELSE 0 END) AS losses,
                        SUM(CASE WHEN p.outcome='DRAW' THEN 1 ELSE 0 END) AS draws
                      FROM match_players p JOIN matches m ON m.id=p.match_id JOIN users u ON u.id=p.user_id
                      WHERE m.status='FINISHED' AND m.game_mode='ARENA' GROUP BY p.user_id,u.display_name
                    ), scores AS (SELECT totals.*,wins*3+draws AS points FROM totals),
                    ranks AS (SELECT scores.*,DENSE_RANK() OVER(ORDER BY points DESC,wins DESC,losses ASC) AS place FROM scores)
                    SELECT * FROM ranks
                    """;

    private List<RankResponse> query(String suffix, Object... args) {
        return db.query(
                RANKS + suffix,
                (r, n) ->
                        new RankResponse(
                                r.getLong("user_id"),
                                r.getString("display_name"),
                                r.getLong("place"),
                                r.getLong("points"),
                                r.getLong("played"),
                                r.getLong("wins"),
                                r.getLong("losses"),
                                r.getLong("draws"),
                                Math.round(r.getLong("wins") * 1000.0 / r.getLong("played"))
                                        / 10.0),
                args);
    }

    public List<RankResponse> page(int limit, int offset) {
        return query(
                " ORDER BY points DESC,wins DESC,losses,user_id LIMIT ? OFFSET ?", limit, offset);
    }

    public Optional<RankResponse> personal(long userId) {
        return query(" WHERE user_id=?", userId).stream().findFirst();
    }

    public List<CampaignRankResponse> campaign(String difficulty, int limit, int offset) {
        return campaignQuery(
                " ORDER BY cleared DESC,score DESC,user_id LIMIT ? OFFSET ?",
                difficulty,
                limit,
                offset);
    }

    public Optional<CampaignRankResponse> campaignPersonal(long userId, String difficulty) {
        return campaignQuery(" WHERE r.user_id=?", difficulty, userId).stream().findFirst();
    }

    private List<CampaignRankResponse> campaignQuery(String suffix, Object... arguments) {
        String query =
                """
            WITH best AS (
              SELECT p.user_id,m.stage_number,MAX(p.score) AS score
              FROM match_players p JOIN matches m ON m.id=p.match_id
              WHERE m.status='FINISHED' AND m.game_mode='CAMPAIGN' AND m.difficulty=? AND p.outcome='WIN'
              GROUP BY p.user_id,m.stage_number
            ), totals AS (
              SELECT user_id,COUNT(*) AS cleared,SUM(score) AS score FROM best GROUP BY user_id
            ), ranks AS (
              SELECT totals.*,DENSE_RANK() OVER(ORDER BY cleared DESC,score DESC) AS place FROM totals
            )
            SELECT r.*,u.display_name FROM ranks r JOIN users u ON u.id=r.user_id
            """;
        return db.query(
                query + suffix,
                (row, index) ->
                        new CampaignRankResponse(
                                row.getLong("user_id"),
                                row.getString("display_name"),
                                row.getLong("place"),
                                row.getLong("cleared"),
                                row.getLong("score")),
                arguments);
    }

    public List<Integer> clearedStages(long userId, String difficulty) {
        return db.query(
                "SELECT DISTINCT m.stage_number FROM match_players p JOIN matches m ON m.id=p.match_id WHERE p.user_id=? AND m.difficulty=? AND m.game_mode='CAMPAIGN' AND m.status='FINISHED' AND p.outcome='WIN' ORDER BY m.stage_number",
                (row, index) -> row.getInt(1),
                userId,
                difficulty);
    }
}
