# Instant và quy ước thời gian

## 1. Quy tắc source

Mốc sự kiện dùng java.time.Instant: createdAt, updatedAt, startedAt, endedAt, savedAt, recoveryDeadline, expiresAt, revokedAt và consumedAt. Các model auth, entity, DTO và runtime đã chuyển sang Instant.

Instant biểu diễn một điểm trên trục thời gian; không mang múi giờ địa phương. [Java 21 Instant](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Instant.html).

- API dùng chuỗi ISO-8601 UTC, ví dụ 2026-10-20T08:15:30Z.
- Khi hiển thị Việt Nam, đổi sang ZoneId.of("Asia/Ho_Chi_Minh"): ví dụ trên là 15:15:30.
- Ngày không có giờ như ngày sinh phù hợp LocalDate.
- Thời lượng phù hợp Duration. Tick mô phỏng/cooldown vẫn là số tick.
- Các bộ đếm millisecond của scheduler/presence không tự chuyển thành trường ngày giờ của entity.

## 2. JPA và JDBC

Schema giữ TIMESTAMP WITH TIME ZONE. JPA map Instant; hibernate.jdbc.time_zone cấu hình UTC. Không dùng @Temporal cho java.time.

pgJDBC chưa hỗ trợ bind/getObject Instant trực tiếp, nhưng hỗ trợ OffsetDateTime cho cột này. [pgJDBC Java time](https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes).

JdbcTimeUtils là cầu nối duy nhất:

- readInstant: JDBC OffsetDateTime → Instant, giữ null.
- toDatabase: Instant → OffsetDateTime UTC trước khi bind SQL.
- Service/DTO không cần biết kiểu trung gian của driver.

Chú ý tham số batch cũng phải chuyển đổi, không chỉ db.update đơn lẻ. MatchService.prepareRecovery và AuthTokenRepository đã dùng cùng tiện ích.

## 3. Độ chính xác và kiểm thử

PostgreSQL timestamp có độ phân giải microsecond, còn Instant có thể có nanosecond. Không so sánh round trip với giả định DB giữ nguyên chín chữ số thập phân. [PostgreSQL datetime](https://www.postgresql.org/docs/17/datatype-datetime.html).

JdbcTimeUtilsTest kiểm tra offset +07:00 tương ứng cùng instant UTC, null và round trip H2. H2FilePersistenceTest kiểm tra mốc hết hạn vẫn đúng khi mở lại DB. Kiểm thử PostgreSQL thật còn cần bổ sung.
