# Bàn giao tiếp tục source

Cập nhật 20/10/2026. Đọc [quy ước](../AGENTS.md), [STATUS](STATUS.md), [luồng backend](20-luong-backend-va-test.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).

## Quyết định hiện tại

- Lỗi Render mới là JDBC không tìm thấy rooms/app_locks dù Flyway schema tank_battle đã version 6 và Hibernate validate đạt. Source trước sửa vẫn startup PostgreSQL mới thành công nên chưa xác nhận commit/env Render hiện tại. Đã chốt spring.datasource.hikari.schema=tank_battle thay map data-source-properties.currentSchema; JDBC/JPA/Flyway phải cùng schema, direct endpoint. URL mẫu vẫn giữ currentSchema=tank_battle.
- Flyway pin 11.20.3 cho cả core/PostgreSQL để hỗ trợ PG18. Không sửa V1–V6: index/constraint đã có trong SQL, nay khai báo thêm @Index/@UniqueConstraint tương ứng ở entity; ddl-auto vẫn validate. Khi đổi DDL thật mới thêm migration V7 trở đi.
- Kiểm chứng mới nhất: 78 test Java/build/Spotless và 34 test PostgreSQL 18.6 đạt; JAR prod thật tự tạo schema/bảng từ DB mới, admin login, /api/admin/me và /api/rooms đạt với URL không có currentSchema; public trống, đủ V1–V6, hết cảnh báo Flyway PG18. Smoke PowerShell/CI đã thêm luồng này. Docker/GitHub/Render thật chưa chạy; không dùng Neon. Xem STATUS để phân biệt các kết quả lịch sử bên dưới.
- Người dùng cần push/redeploy đúng commit, import env hiện tại nếu Dashboard còn cũ, giữ cặp PEM và bootstrap admin env. Nếu có SPRING_DATASOURCE_HIKARI_SCHEMA ghi đè thì dùng tank_battle. Không sửa dữ liệu/history Neon để chữa search_path. Windows tar thiếu xz; lần kiểm tra này giải nén archive portable bằng Python vào build, chỉ là workaround tại máy kiểm thử.

- CI mới: verify và docker song song, Ready for Render vẫn chờ cả hai success. Gradle cache bằng setup-gradle@v5, một lệnh build postgresTest; task test bỏ env TEST_DATABASE_* để giữ H2 riêng, postgresTest luôn chạy PostgreSQL test thật. Docker Buildx cache GHA, load:true phục vụ smoke, không push registry. Đã kiểm YAML/task graph/22 test auth-source, chưa chạy workflow/Docker thật hoặc benchmark thời gian. Cache cải thiện từ các lần đã có cache; Render cache riêng.

- Đã tìm và xác nhận không có jwt-private.pem/jwt-public.pem trong source/Desktop trước lượt hỗ trợ Secret Files. Đã chạy GenerateJwtKeys.java để tạo cặp RSA mới tại secrets/jwt-private.pem và secrets/jwt-public.pem; không in nội dung, không thay khóa trên Render hoặc truy cập Neon. Cần người dùng thêm cả hai file lên Render Secret Files; file local được gitignore và dockerignore.

- Theo yêu cầu mới nhất, đã bỏ application-local.yml, mọi H2 runtime và bốn lớp LocalDemo*. Ứng dụng chỉ có prod/PostgreSQL, mặc định spring.profiles.default=prod và Docker ENV prod; bootJar không cần cờ -Pproduction. H2 chỉ testRuntimeOnly, profile test chỉ trong src/test. JWT kiểm cả default prod để chặn khóa tạm. Đã chạy spotlessApply/build: 77 test đạt, JAR không có H2/local/test/demo/env/PEM và mặc định prod. Chưa chạy lại PostgreSQL/prod startup hoặc Docker/Render/RAM thực tế. Xem STATUS.
- Log Render cũ trước lượt sửa schema: build thành công, startup dừng vì thiếu `/etc/secrets/jwt-public.pem`. Cần thêm cả hai RSA Secret Files rồi redeploy, import BOOTSTRAP_ADMIN_* để admin được tạo khi DB chưa có. Đổi profile không giải quyết file khóa thiếu; không tự sinh khóa production mỗi restart. Flyway/bootstrap hiện có đã init schema/admin, không thêm seed mật khẩu vào migration.

- Backend Java 21, Boot 3.5.16, Gradle Wrapper 8.14.6/Groovy DSL; không Maven hoặc Node/npm. UI chỉ làm khi người dùng yêu cầu.
- Import TestDescriptor/TestResult đã bỏ cùng listener đếm test không cần thiết; dùng report gốc của Gradle. Nếu IDE còn báo đỏ, mở thư mục/build.gradle bằng Gradle Wrapper và JVM 21, Reload Gradle Project.
- Tài liệu/hướng dẫn đầy đủ ở .agents; root README/AGENTS là điểm vào giúp người/AI tự tìm. Cập nhật tài liệu tại đây sau mỗi lượt sửa; không ghi dữ liệu env/token/key ra log hoặc tài liệu.
- Các test backend và hợp đồng source nằm trong src/test/java. Không tạo lại tests Node/package.json/Prettier. .editorconfig chỉ thống nhất format Java/Gradle giữa IDE; Java được Spotless kiểm tra.
- tools có script backend đang dùng: PostgreSQL portable, smoke prod profile/container, kiểm DB chỉ đọc, JWT, import/export Render env. Giữ script; không giữ binary/cache/cluster thử trong source. Đầu ra dưới build, không .tools.
- Cho phép commit .env/render.env theo yêu cầu chủ đồ án; Docker vẫn loại env/PEM. .data/secrets giữ riêng để không mất H2/cặp RSA. Không tự đổi lại chính sách env.
- Production dùng database neondb, schema tank_battle. Flyway schemas/default-schema, Hibernate default_schema và Hikari schema cùng giá trị tank_battle; URL mẫu cũng có currentSchema tương ứng. App/Flyway dùng direct endpoint + Hikari 5, không pooler do JDBC/native SQL cần search_path ổn định; không tự đổi lại pooler khi chưa qualify toàn bộ SQL hoặc thiết kế schema tương ứng.
- PostgreSQL test/smoke kiểm cùng schema tank_battle, public không có bảng. H2 chỉ trong test; đổi default schema không di chuyển dữ liệu cũ từ public. Không tự migrate/copy/drop/baseline dữ liệu Neon.

## Tính năng và luồng đọc code

Auth/JWT/Google/refresh/CSRF; phòng/membership/concurrency; engine Arena/Campaign/cửa/độ khó/địch/item; checkpoint/recovery; history/ranking; offline save backend đều đã có implementation. Xem STATUS để biết test mới đã/chưa chạy.

Đọc common/constant/validation/exception/i18n → auth/config/controller/service/repository → business/controller/service/repository/mapper → model/GameState/engine/StateCodec → runtime/GameCoordinator/websocket. [Tài liệu 21](21-doc-code-cau-hinh-service.md) chỉ caller, transaction, khóa và tác động RAM/DB.

BootstrapAdminInitializer là ApplicationRunner có transaction, dùng guard V6 trong BootstrapAdminRepository trước đọc/insert. Ứng dụng đọc BOOTSTRAP_ADMIN_PASSWORD khi tạo đầu, không có mật khẩu mặc định; test có cấu hình riêng. Giữ nguyên admin đã có; không nâng quyền USER trùng tên. API /api/admin/me và matcher ROLE_ADMIN đã có; chưa có CRUD API quản trị catalog. Không nhầm vai trò ADMIN với bỏ qua mọi kiểm tra chủ sở hữu của người chơi.

## Các điểm dễ sửa sai

- API Bearer và web session tách biệt; endpoint auth dùng cookie vẫn cần CSRF.
- Refresh replay phải commit revoke trước trả lỗi; noRollbackFor=AuthFailure chỉ cho refresh.
- Khóa room trước user/match; nhiều room theo ID tăng dần. Tick chỉ RAM, ghi checkpoint/kết quả ở repository theo batch.
- FINISHED/ABORTED là terminal; retry không cộng điểm lần nữa. Giữ matchId khi restore; offline không tạo ranking online.
- Snapshot chứa cấu hình trận; không nạp catalog mới làm thay đổi trận đang lưu. Tất cả disconnect phải pause, deadline không gia hạn khi restart.
- Instant qua JdbcTimeUtils, SQL timestamptz; deadline microsecond. JDBC write cập nhật audit và tránh entity JPA cũ trong persistence context.
- Mọi Java dùng kiểu tường minh; mô tả tiếng Việt sát từng hàm, DTO/model ở package riêng, auth/common/business không phá chiều dependency.
- Admin guard là dữ liệu hệ thống, password không nằm trong migration. Không sửa V1–V5 đã phát hành; V6 bổ sung guard riêng. Smoke/CI kiểm sáu migration.

## Kiểm tra và tiếp tục

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean build
.\tools\test-postgres.ps1 -ProductionSmoke
.\gradlew.bat bootRun # Mặc định prod; cần biến DB/JWT/admin trước khi chạy.
```

Kết quả cuối sau cấu hình tank_battle: 75 test Java build không cache đạt, PostgreSQL 34 test đạt, prod-profile JAR health UP/auth 401 en/admin seed/sáu migration đúng schema; public không có bảng. Xem STATUS. Gradle report ở build/reports/tests; log script build/verification. Chưa chạy GitHub Actions hoặc Docker/Render public thật.

Sau sửa tài liệu, SourceContractTest 6/6 và exporter chạy với fixture giả đạt. Git audit cuối có 248 file source theo chính sách env hiện tại, không bao gồm build/key/H2. Không cộng lượt lọc sáu test vào số test case độc lập của clean build.

Đã dùng `gradlew clean` sau nghiệm thu để dọn đầu ra/binary/fixture/audit tạm; không còn JAR/report dưới build. Chạy build/test sẽ sinh lại; không mang output/cache sang source bàn giao. .data/secrets không bị xóa.

Render Free + Neon, repo hieuv526034/tank-battle-web, URL tank-battle-web.onrender.com. Người dùng tự push. **Trước deploy import lại render.env:** datasource/Flyway đã đổi sang direct/neondb/currentSchema=tank_battle, có BOOTSTRAP_ADMIN_*; giữ RSA Secret Files. Auto-Deploy After CI Checks Pass; chỉ phát hành khi không còn trận chơi vì chưa có ownership runtime. Không dùng Neon test/migrate thử. Nếu public đã có dữ liệu từ bản cũ, cần chuyển schema/history sau backup; đổi config không tự chuyển bảng cũ.

Khi người dùng yêu cầu UI, mới xây Thymeleaf/Canvas/engine JS/IndexedDB/Service Worker/reconnect và test browser. Trước mở rộng quản trị catalog cần thiết kế API ADMIN, validation/versioning và bảo vệ cấu hình của trận đang chạy.
