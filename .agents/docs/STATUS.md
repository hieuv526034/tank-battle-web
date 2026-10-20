# Trạng thái backend

Cập nhật 20/10/2026, Asia/Saigon. Backend trước UI; không coi kế hoạch gốc là bằng chứng tính năng đã chạy.

## Đã triển khai

- Java 21/Spring Boot 3.5.16/Gradle Wrapper 8.14.6; package auth/common/business, business chia tầng chung.
- JWT RS256, refresh rotation/replay/revoke có commit, CSRF, rate limit, Google OIDC tùy chọn; mã lỗi ValidateCode/bundle vi/en và Accept-Language chung.
- Phòng/create/join/ready/start/leave, tranh chấp ghế/membership; WebSocket ticket/Origin/member/sequence/sync/revoke sau commit.
- Engine Java Arena/Campaign, độ khó/cửa/wave/địch/item/buff/điểm; checkpoint schema 2, pause/recovery giữ matchId và deadline.
- Kết toán batch/idempotent, history riêng tư, ranking online FINISHED; offline cloud save ba slot/revision, không cộng ranking.
- Instant và JDBC bridge; ứng dụng prod/PostgreSQL, H2 chỉ trong test, Flyway V1–V6 và JPA validate. Migration V6 chỉ thêm guard bootstrap admin.
- Bootstrap admin trên DB ứng dụng: PBKDF2, transaction/guard DB, giữ mật khẩu đã có, không tự nâng USER hoặc mở khóa admin. /api/admin/** yêu cầu ADMIN; GET /api/admin/me đã có Swagger.
- Javadoc tiếng Việt cho các hàm cấu hình/service/runtime/engine/socket; tài liệu luồng đầy đủ trong .agents/docs.

## Lượt thay đổi hiện tại

### Sửa startup Render: JDBC sai schema và cảnh báo PostgreSQL 18

- Log Render mới: Flyway đã validate đủ 6 migration trong tank_battle và Hibernate đã tạo EntityManagerFactory, nhưng JDBC không tìm thấy rooms/app_locks. Đây là dấu hiệu search_path của kết nối JDBC khác schema Flyway/JPA, không phải chưa có cơ chế tạo bảng. URL trong log không có currentSchema; host không phải pooler. Không xác nhận được commit/env đang chạy trên Render.
- Source trước sửa vẫn vượt qua 34 test PostgreSQL 18.6 và prod startup trên DB mới với URL không có currentSchema, nên chưa tái hiện được lỗi Render bằng source hiện tại. Đã thay map pgJDBC currentSchema bằng spring.datasource.hikari.schema=tank_battle để pool đặt schema cho mọi connection, không phụ thuộc tham số URL. ProductionProfileTest kiểm binding Hikari thật, không mở DB; PostgreSQL integration dùng cùng thuộc tính schema.
- Bổ sung @Index cho RoomPlayer/MatchPlayer/Match và @UniqueConstraint cho User/Room/RoomPlayer đúng các index/constraint đã có từ V1/V4. Không tạo index trùng PK/unique; không thay checksum V1–V6 hoặc thêm migration khi DDL không đổi. Flyway tiếp tục tạo schema/bảng/index, Hibernate giữ ddl-auto=validate.
- Pin flyway-core và flyway-database-postgresql cùng 11.20.3, hỗ trợ PostgreSQL 18; giữ Boot 3.5.16/pgJDBC 42.7.14. Prod JAR mới không còn cảnh báo Flyway hỗ trợ tối đa PostgreSQL 17. Cảnh báo thiếu templates còn vì UI chưa triển khai.
- Mở rộng smoke PowerShell và container CI: CSRF → đăng nhập admin bootstrap → /api/admin/me → /api/rooms, kiểm quyền ADMIN và danh sách phòng trống. Health/JPA riêng có thể bỏ sót lỗi JDBC schema.
- Đã chạy spotlessApply build copyPostgresDriver: **78 test, 0 failure/error/skipped**, build/format đạt. Sau đó test-postgres.ps1 -ProductionSmoke: **34 test PostgreSQL 18.6 đạt**, JAR thật profile prod trên DB mới health UP, auth 401/en, admin login/API và rooms API đạt; sáu migration/admin trong tank_battle, public không có bảng. Log không có ERROR hoặc cảnh báo Flyway cũ; stderr startup rỗng. Cluster/JVM/key test đã dừng/dọn, không kết nối Neon.
- Parser PowerShell/Bash, cú pháp Python của container smoke và git diff --check đạt. Sau cập nhật docs, chạy lại riêng 6 SourceContractTest đạt; không cộng lượt lọc vào 78 test độc lập. JAR chứa cả hai module Flyway 11.20.3. Windows tar thiếu bộ giải nén xz; đã giải nén archive test bằng thư viện chuẩn Python vào build/postgres-test/server, không thêm dependency Python vào ứng dụng. Docker/GitHub Actions/Render thật chưa chạy; bản Python trong container smoke mới kiểm cú pháp, chưa chạy container trên máy này.
- Bước Render: push bản source mới, import lại render.env nếu Dashboard còn URL cũ, giữ hai RSA Secret Files và BOOTSTRAP_ADMIN_*. Nếu có biến SPRING_DATASOURCE_HIKARI_SCHEMA ghi đè thì đặt tank_battle. Redeploy đúng commit mới và kiểm readiness/login/rooms. Không repair/clean/drop/baseline DB để xử lý lỗi này.

### Các lượt trước trong cùng cuộc trao đổi

- Tối ưu GitHub CI: verify/docker song song, setup-gradle cache, gộp build postgresTest bỏ clean, Docker Buildx cache GHA. Task test tách TEST_DATABASE_* khỏi JVM H2, source/docs thành cache inputs; giữ đủ test và deploy-ready chờ cả hai. YAML được parse bằng SnakeYAML đạt, không có duplicate key; task graph build postgresTest đạt. Chạy AuthSystemTest 16 + SourceContractTest 6 với TEST_DATABASE_URL cố ý là host ngoài: 22 test đạt, chứng minh test vẫn dùng H2 và không nối host ngoài. git diff --check đạt. Chưa chạy workflow GitHub/Docker thật hoặc đo mức tăng tốc; actionlint không tải được do DNS api.github.com, đã dùng parser YAML sẵn có thay thế. Không truy cập Neon.

- Đã tìm và xác nhận không có jwt-private.pem/jwt-public.pem trong source/Desktop trước lượt hỗ trợ Secret Files. Đã chạy GenerateJwtKeys.java để tạo cặp RSA mới tại secrets/jwt-private.pem và secrets/jwt-public.pem; không in nội dung, không thay khóa trên Render hoặc truy cập Neon. Cần người dùng thêm cả hai file lên Render Secret Files; file local được gitignore và dockerignore.

- Theo yêu cầu chỉ dùng prod: xóa application-local.yml, bỏ bốn lớp LocalDemo* và các biến LOCAL_DEMO_* khỏi exporter/Blueprint/env. H2 chỉ còn testRuntimeOnly, mọi JAR/bootRun dùng PostgreSQL; bỏ cờ đóng gói -Pproduction. application.yml mặc định prod, Docker giữ ENV prod. Google chỉ là cấu hình OAuth bổ sung, test chỉ có trong src/test.
- JWT dùng acceptsProfiles để chặn khóa tạm cả khi prod là default profile. Thêm ProductionProfileTest kiểm nạp default prod/PostgreSQL/cookie HTTPS, chặn khóa tạm và cách profile test tách DB. H2FilePersistenceTest chuyển từ seed demo sang bootstrap admin qua transaction, kiểm giữ dữ liệu sau đóng/mở DB và không INSERT trùng.
- Đã chạy spotlessApply và build: **77 test, 0 failure/error/skipped**, build/Spotless đạt (10 task, 9 executed). Kiểm ZIP JAR: mặc định prod, không có H2, application-local/test.yml, lớp LocalDemo/test, PEM hoặc render.env. git diff --check đạt. Đã cập nhật hướng dẫn chạy/DB/bootstrap/CI và bàn giao.
- Chưa chạy lại PostgreSQL integration hoặc prod-profile startup sau lượt này; chưa chạy Docker/Render thật hoặc đo RAM. Không kết nối/ghi Neon. Cần nạp env DB/JWT/admin trước khi chạy ứng dụng; log Render vẫn cần xử lý hai RSA Secret Files. Dữ liệu .data/secrets cũ không bị xóa.

## Lịch sử thay đổi trước khi bỏ local

- Siết Docker production sau log Render thiếu `/etc/secrets/jwt-public.pem`: runtime mặc định SPRING_PROFILES_ACTIVE=prod; build dùng `-Pproduction=true bootJar`, loại driver H2 và application-local.yml khỏi JAR, .dockerignore loại src/test. Trước sửa Docker cũng đã chỉ chạy bootJar, không chạy test/Spring/H2 lúc build. Giữ kiểm thử GitHub CI và bootstrap/Flyway hiện có.
- Kiểm chứng lượt Docker production: dry-run chỉ compileJava/processResources/classes/resolveMainClassName/bootJar; đóng gói thật thành công (4 task executed). Kiểm ZIP JAR xác nhận không có H2/local config/test class/PEM/render.env; có prod config, BootstrapAdminInitializer và V6. Chạy riêng AuthSystemTest 16 + SourceContractTest 6 với `-Pproduction=true`: 22 test, 0 failure/error/skipped. Chưa chạy lại PostgreSQL/prod startup, Docker (máy không có CLI), Render thật hoặc đo RAM. Không đọc/ghi Neon. Gradle lần đầu bị sandbox chặn mạng, chạy lại với quyền tải Wrapper/dependency đã thành công.

- Bỏ listener afterSuite chỉ để in tổng test và hai import TestDescriptor/TestResult trong build.gradle. Import ban đầu hợp lệ với Gradle API; CLI help chạy thành công, nhưng IDE cần import project theo Gradle/JDK 21. Dùng XML/HTML report gốc của Gradle.
- Gom docs vào .agents/docs, hướng dẫn đầy đủ vào .agents/AGENTS.md và mục lục .agents/README.md. Root AGENTS.md/README.md chỉ là điểm vào ngắn cho người/AI tự tìm tài liệu. Đã cập nhật link tương đối khi chuyển thư mục.
- Xóa tests Node, package.json/package-lock, .prettierrc và .prettierignore. Chuyển sáu hợp đồng quan trọng sang JUnit SourceContractTest; CI bỏ setup-node/npm. Backend không cần Node/npm.
- tools vẫn dùng cho PostgreSQL test, JWT và CI/Render; binary/cluster/key test/log ở build, cache Gradle ở vị trí chuẩn của người dùng. Không tạo .tools.
- Thêm bootstrap admin, migration V6, DTO/controller quản trị, sáu test tích hợp trong AuthSystemTest. Cập nhật env mẫu/thật, exporter, Render Blueprint và smoke test theo V6.
- .env/render.env được phép commit theo quyết định chủ đồ án; chỉ thêm biến bootstrap, không in giá trị. Docker loại env/PEM khỏi image; H2/RSA giữ riêng.
- Chốt production database neondb/schema tank_battle. Flyway tự tạo schema và đặt history ở đó; Hibernate default_schema/pgJDBC currentSchema thống nhất. App/Flyway dùng direct endpoint và Hikari 5 kết nối vì JDBC/native SQL cần search_path ổn định. Cập nhật hai env, ví dụ, exporter, CI/smoke và tài liệu; không sửa nội dung V1–V6.
- PostgreSQL integration cũng dùng tank_battle; prod smoke kiểm không có bảng trong public, đủ sáu migration có version và admin trong tank_battle. H2 local giữ schema mặc định để không thay dữ liệu hiện có.

## Kiểm thử trước khi bỏ local (lịch sử)

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
- Hiện đã có Git repository; người dùng tự push repo hieuv526034/tank-battle-web. Workflow GitHub/Docker/public Render chưa được chạy thật tại máy này.
- Render URL người dùng cung cấp chưa được xác nhận health public qua công cụ. Env commit không tự cập nhật Dashboard.
- Chưa benchmark tải/query plan/HA hoặc hard-kill đúng thời điểm ghi DB; một runtime instance, chưa ownership xuyên process. Chỉ deploy khi không còn trận RUNNING/RECOVERING và không ai chơi trong lúc phát hành.
- Crash có thể mất đoạn từ checkpoint cuối, khoảng hai giây khi storage khỏe. Test đạt không phải cam kết không còn mọi lỗi.
- Neon từng được kiểm metadata chỉ đọc: PostgreSQL 18.6/public, không chạy Flyway hoặc ghi dữ liệu. Không truy cập lại Neon trong lượt sửa này.

Tiếp tục bằng [HANDOFF](HANDOFF.md), [quy ước](../AGENTS.md), [mục lục](../README.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).
