package vn.edu.utc.tankbattle.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Kiểm tra null, UTC và round trip trên H2; PostgreSQL cần integration test riêng. */
class JdbcTimeUtilsTest {
    @Test
    void convertsOffsetToSameInstantAndKeepsNullableColumns() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("saved_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-10-20T15:15:30+07:00"));
        assertThat(JdbcTimeUtils.readInstant(row, "saved_at"))
                .isEqualTo(Instant.parse("2026-10-20T08:15:30Z"));
        assertThat(JdbcTimeUtils.readInstant(row, "ended_at")).isNull();
        assertThat(JdbcTimeUtils.toDatabase(null)).isNull();
        assertThat(JdbcTimeUtils.toDatabase(Instant.parse("2026-10-20T08:15:30Z")).getOffset())
                .isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void roundTripTimestampWithTimeZoneWithoutChangingTheInstant() throws Exception {
        String url = "jdbc:h2:mem:time_" + UUID.randomUUID();
        Instant expected = Instant.parse("2026-10-20T08:15:30.123456Z");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE moments(saved_at TIMESTAMP WITH TIME ZONE, ended_at TIMESTAMP WITH TIME ZONE)");
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO moments(saved_at) VALUES(?)")) {
                insert.setObject(1, JdbcTimeUtils.toDatabase(expected));
                insert.executeUpdate();
            }
            try (ResultSet rows = statement.executeQuery("SELECT * FROM moments")) {
                assertThat(rows.next()).isTrue();
                assertThat(JdbcTimeUtils.readInstant(rows, "saved_at")).isEqualTo(expected);
                assertThat(JdbcTimeUtils.readInstant(rows, "ended_at")).isNull();
            }
        }
    }
}
