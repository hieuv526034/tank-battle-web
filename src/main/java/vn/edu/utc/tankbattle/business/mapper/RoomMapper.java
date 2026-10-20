package vn.edu.utc.tankbattle.business.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;

/** Ánh xạ projection SQL và ghép member đã đọc theo lô; không tự gọi repository. */
public final class RoomMapper {
    private RoomMapper() {}

    public static RoomResponse header(ResultSet row) throws SQLException {
        return new RoomResponse(
                row.getLong("id"),
                row.getString("code"),
                row.getString("name"),
                row.getLong("host_user_id"),
                row.getInt("capacity"),
                row.getString("status"),
                List.of());
    }

    public static MemberResponse member(ResultSet row) throws SQLException {
        return new MemberResponse(
                row.getLong("user_id"),
                row.getString("display_name"),
                row.getInt("seat"),
                row.getBoolean("ready"));
    }

    public static RoomResponse withMembers(RoomResponse room, List<MemberResponse> members) {
        return new RoomResponse(
                room.id(),
                room.code(),
                room.name(),
                room.hostUserId(),
                room.capacity(),
                room.status(),
                members);
    }
}
