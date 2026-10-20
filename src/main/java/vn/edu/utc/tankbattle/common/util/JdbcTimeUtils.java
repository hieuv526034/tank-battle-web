package vn.edu.utc.tankbattle.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Cầu nối duy nhất giữa Instant của ứng dụng và TIMESTAMP WITH TIME ZONE của JDBC.
 *
 * <p>pgJDBC hỗ trợ OffsetDateTime, không bind/getObject trực tiếp bằng Instant. Offset chỉ tồn tại
 * tại ranh giới SQL; entity, DTO và service dùng Instant.
 */
public final class JdbcTimeUtils {
    /** Ẩn constructor của cầu nối thời gian JDBC dùng chung. */
    private JdbcTimeUtils() {}

    /**
     * Đọc TIMESTAMP WITH TIME ZONE qua kiểu OffsetDateTime mà driver hỗ trợ rồi chuyển về Instant.
     * Giữ null cho các mốc chưa xảy ra như endedAt/revokedAt, thay vì tự tạo một thời điểm giả.
     */
    public static Instant readInstant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * Chuyển Instant sang OffsetDateTime ở UTC để bind tham số JDBC/batch. Cô lập kiểu driver tại
     * đây để entity/DTO/service tiếp tục dùng Instant; null đi qua không thay đổi.
     */
    public static OffsetDateTime toDatabase(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
