# Trạng thái backend

Cập nhật 20/10/2026, Asia/Saigon. Backend trước UI; không coi kế hoạch gốc là bằng chứng tính năng đã chạy.

## Đã triển khai

- Java 21/Spring Boot 3.5.16/Gradle Wrapper 8.14.6; package auth/common/business, business chia tầng chung.
- JWT RS256, refresh rotation/replay/revoke có commit, CSRF, rate limit, Google OIDC tùy chọn; mã lỗi ValidateCode/bundle vi/en và Accept-Language chung.
- Phòng/create/join/ready/start/leave, tranh chấp ghế/membership; WebSocket ticket/Origin/member/sequence/sync/revoke sau commit.
- Engine Java Arena/Campaign, độ khó/cửa/wave/địch/item/buff/điểm; checkpoint schema 2, pause/recovery giữ matchId và deadline.
- Kết toán batch/idempotent, history riêng tư, ranking online FINISHED; offline cloud save ba slot/revision, không cộng ranking.
- Instant và JDBC bridge; H2 file local, PostgreSQL production, Flyway V1–V6 và JPA validate. Migration V6 chỉ thêm guard bootstrap admin.
- Bootstrap admin trên local/prod: PBKDF2, transaction/guard DB, giữ mật khẩu đã có, không tự nâng USER hoặc mở khóa admin. /api/admin/** yêu cầu ADMIN; GET /api/admin/me đã có Swagger.
- Javadoc tiếng Việt cho các hàm cấu hình/service/runtime/engine/socket; tài liệu luồng đầy đủ trong .agents/docs.

## Lượt thay đổi hiện tại

- Bỏ listener afterSuite chỉ để in tổng test và hai import TestDescriptor/TestResult trong build.gradle. Import ban đầu hợp lệ với Gradle API; CLI help chạy thành công, nhưng IDE cần import project theo Gradle/JDK 21. Dùng XML/HTML report gốc của Gradle.
- Gom docs vào .agents/docs, hướng dẫn đầy đủ vào .agents/AGENTS.md và mục lục .agents/README.md. Root AGENTS.md/README.md chỉ là điểm vào ngắn cho người/AI tự tìm tài liệu. Đã cập nhật link tương đối khi chuyển thư mục.
- Xóa tests Node, package.json/package-lock, .prettierrc và .prettierignore. Chuyển sáu hợp đồng quan trọng sang JUnit SourceContractTest; CI bỏ setup-node/npm. Backend không cần Node/npm.
- tools vẫn dùng cho PostgreSQL test, JWT và CI/Render; binary/cluster/key test/log ở build, cache Gradle ở vị trí chuẩn của người dùng. Không tạo .tools.
- Thêm bootstrap admin, migration V6, DTO/controller quản trị, sáu test tích hợp trong AuthSystemTest. Cập nhật env mẫu/thật, exporter, Render Blueprint và smoke test theo V6.
- .env/render.env được phép commit theo quyết định chủ đồ án; chỉ thêm biến bootstrap, không in giá trị. Docker loại env/PEM khỏi image; H2/RSA giữ riêng.
- Chốt production database neondb/schema tank_battle. Flyway tự tạo schema và đặt history ở đó; Hibernate default_schema/pgJDBC currentSchema thống nhất. App/Flyway dùng direct endpoint và Hikari 5 kết nối vì JDBC/native SQL cần search_path ổn định. Cập nhật hai env, ví dụ, exporter, CI/smoke và tài liệu; không sửa nội dung V1–V6.
- PostgreSQL integration cũng dùng tank_battle; prod smoke kiểm không có bảng trong public, đủ sáu migration có version và admin trong tank_battle. H2 local giữ schema mặc định để không thay dữ liệu hiện có.

## Kiểm thử lượt hiện tại

Sau khi chốt schema tank_battle:

- `spotlessApply` rồi `clean build copyPostgresDriver --no-build-cache`: **75 test Java, 0 failure/error/skipped**, build/Spotless đạt, 12 task executed. Báo cáo XML/HTML ở build/test-results/test và build/reports/tests/test.
- PostgreSQL 18.6 riêng: **34 test, 0 failure/error**, gồm AuthSystemTest 16 và BusinessFlowTest 18. JDBC/JPA/Flyway cùng dùng tank_battle; test login admin kiểm current_schema và không có bảng ứng dụng trong public.
- JAR thật profile prod: health UP, auth 401/AUTH_001 tiếng Anh; sáu migration có version trong tank_battle.flyway_schema_history, admin trong tank_battle.users, public không có bảng. JVM smoke PID 888 và cluster/key test đã dừng/dọn; không dùng Neon.
- Parser PowerShell/Bash đạt. Kiểm env/mẫu: đúng neondb, direct endpoint, currentSchema=tank_battle; không in giá trị. JAR không chứa env/PEM.
- Sau sửa tài liệu, chạy riêng **6 SourceContractTest đạt**, gồm kiểm link trong .agents; không cộng thành 81 test độc lập. Exporter được chạy với fixture giả, xác nhận tạo đúng direct/neondb/schema, không đọc/ghi Neon.
- Git audit tạm xác nhận 248 file source dự kiến commit, gồm .agents, env và Wrapper; key/H2/build/cache bị loại. Repo thật chưa init/stage/commit/push. Không có package.json/Prettier/tests Node hoặc .tools; giữ tools vì CI/DB scripts đang dùng.
- Sau nghiệm thu đã chạy `gradlew clean` đạt để dọn toàn bộ build, gồm binary/cluster PostgreSQL, fixture và Git audit tạm. JAR/report/log dưới build không còn được giữ; chạy lại build/test sẽ sinh lại. Kết quả thực đã ghi tại đây; .data/secrets và source/env được giữ nguyên.

Cảnh báo còn thấy: API deprecated trong BusinessFlowTest, JVM agent/CDS từ test Mockito, thiếu thư mục templates vì UI chưa triển khai. Các cảnh báo không làm build/startup thất bại. Docker/container và GitHub Actions vẫn cần chạy thật sau push vì máy không có Docker CLI.

Lệnh nghiệm thu:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat --no-daemon --no-build-cache clean build copyPostgresDriver
.\tools\test-postgres.ps1 -ProductionSmoke
```

Báo cáo Java: build/reports/tests/test và build/test-results/test. PostgreSQL: build/reports/tests/postgresTest. Log script ở build/verification. Không dùng Neon production để test.

## Giới hạn và việc chưa kiểm chứng

- Chưa có UI/Canvas, JS engine, IndexedDB/Service Worker hoặc offline browser thật; làm khi người dùng yêu cầu.
- Có vai trò/admin bootstrap và API kiểm quyền; catalog gameplay hiện ở JSON, chưa có CRUD API hay màn hình quản trị catalog.
- Google thật cần OAuth Client; chưa thử callback thật. Session web ở RAM, restart cần đăng nhập lại.
- Thư mục chưa có Git/remote; người dùng tự push repo hieuv526034/tank-battle-web. Workflow GitHub/Docker/public Render chưa được chạy thật tại máy này.
- Render URL người dùng cung cấp chưa được xác nhận health public qua công cụ. Env commit không tự cập nhật Dashboard.
- Chưa benchmark tải/query plan/HA hoặc hard-kill đúng thời điểm ghi DB; một runtime instance, chưa ownership xuyên process. Chỉ deploy khi không còn trận RUNNING/RECOVERING và không ai chơi trong lúc phát hành.
- Crash có thể mất đoạn từ checkpoint cuối, khoảng hai giây khi storage khỏe. Test đạt không phải cam kết không còn mọi lỗi.
- Neon từng được kiểm metadata chỉ đọc: PostgreSQL 18.6/public, không chạy Flyway hoặc ghi dữ liệu. Không truy cập lại Neon trong lượt sửa này.

Tiếp tục bằng [HANDOFF](HANDOFF.md), [quy ước](../AGENTS.md), [mục lục](../README.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).
