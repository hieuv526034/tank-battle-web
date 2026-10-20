package vn.edu.utc.tankbattle.business.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.utc.tankbattle.business.dto.response.MatchResponse;
import vn.edu.utc.tankbattle.business.dto.response.ResultResponse;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;
import vn.edu.utc.tankbattle.common.util.JdbcTimeUtils;

/** Projection SQL tách khỏi nghiệp vụ; payload chỉ dùng nội bộ, thời gian trả Instant UTC. */
public final class MatchMapper {
    private MatchMapper() {}

    public static MatchResponse metadata(ResultSet row) throws SQLException {
        return new MatchResponse(
                row.getString("id"),
                row.getLong("room_id"),
                row.getString("code"),
                row.getString("name"),
                row.getString("status"),
                JdbcTimeUtils.readInstant(row, "started_at"),
                JdbcTimeUtils.readInstant(row, "ended_at"),
                row.getString("end_reason"),
                JdbcTimeUtils.readInstant(row, "recovery_deadline_at"),
                row.getString("payload"),
                GameMode.valueOf(row.getString("game_mode")),
                Difficulty.valueOf(row.getString("difficulty")),
                row.getInt("stage_number"));
    }

    public static ResultResponse result(ResultSet row) throws SQLException {
        return new ResultResponse(
                row.getLong("user_id"),
                row.getString("display_name"),
                row.getString("outcome"),
                (Integer) row.getObject("final_hp"),
                row.getInt("kills"),
                row.getInt("score"));
    }
}
