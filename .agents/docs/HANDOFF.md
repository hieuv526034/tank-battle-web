# Bàn giao tiếp tục source

Cập nhật 21/10/2026. Đọc [quy ước](../AGENTS.md), [STATUS](STATUS.md), [luồng backend](20-luong-backend-va-test.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).

## Tiếp tục sau lượt hình ảnh/hiệu năng và nghiệm thu 21/10/2026

- Đọc [27](27-hinh-anh-hieu-nang-va-luong-game.md). Cache địa hình/sprite và snapshot hiển thị không được ghi ngược state; nội suy chỉ phục vụ hình ảnh. HUD dùng WeakMap theo element, không đưa lại innerHTML không đổi vào mỗi tick.
- Giữ chú thích tiếng Việt sát từng luồng và format toàn bộ JS/HTML/CSS. Không thêm package.json/Node tests/frontend build hoặc thay engine/migration khi chỉ đổi hình.
- authUnavailable phản ánh lỗi truy cập server, không phải quyền đăng nhập. Trang dùng IndexedDB chờ authReady; không chọn scope chỉ bằng navigator.onLine hoặc mở guest trước khi khôi phục user.
- Người dùng đã cho phép test tạo tài khoản/phòng/chơi/save trên H2 tạm, thay giới hạn chỉ xem UI trước đó. Các luồng đã kiểm và giới hạn xem STATUS/27; Java/preview không chứng minh parity JS/Java hay đa ngón trên máy thật.
- Quyền mới: người dùng yêu cầu commit và push với nội dung tiếng Việt chi tiết rồi tắt máy sau commit 10 phút. Không áp dụng lại hướng dẫn lịch sử “không tự commit/push” cho lượt này. Giữ nguyên chính sách env/secrets đã có, không in giá trị.
- Trước lượt nghiệm thu tiếp cần mở lại preview test riêng; hai JVM 18081 đã dừng. Tab thử đóng/viewport reset; giữ tab người dùng 18080. Chưa test Render/Google thật, FPS/RAM điện thoại hoặc online nhiều người/recovery restart.

## Tiếp tục sau lượt format/theme/mobile 21/10/2026

- Đọc [tài liệu 26](26-doc-ui-theme-va-mobile.md). JS/HTML/CSS đã format, có mô tả tiếng Việt sát hàm; giữ cách viết này khi mở rộng. Không thêm package.json/Node tests/Prettier hoặc frontend build bắt buộc.
- theme.js là script đồng bộ trước CSS; app.js chỉ gửi sự kiện theme-preference. Giữ đổi theme không dựng lại main/Canvas; palette sáng/tối, localStorage và asset precache đã có. Nút topbar đổi nhanh, Hướng dẫn có chế độ theo thiết bị.
- GameControls theo từng pointerId; không quay lại một boolean chung cho mọi ngón. Giữ capture/cancel/reset, d-pad trượt và bàn phím đồng thời. Breakpoint menu trong matchMedia phải khớp CSS, gồm cửa sổ ngang thấp; dùng any-pointer:coarse cho nút cảm ứng.
- Đã chạy full build/Spotless 81 test Java và syntax JS; sau chỉnh source cuối chạy lại 24 test auth/source cùng bootJar đạt. Report XML hiện là 24 test được lọc, không phải full suite còn 24. Browser chỉ xem bố cục/theme, 320/390 và 844x390, update worker, không tràn ngang/console error ở bản cuối. Ảnh và giới hạn xem STATUS. Preview 18081 đã dừng, tab thử đã đóng và viewport đã reset.
- Người dùng vẫn chỉ cho xem UI và test Java. Chưa được chơi/tạo tài khoản/phòng/trận/save bằng browser/API preview. Đa ngón trên điện thoại thật, offline gameplay/cloud, engine parity và Render vẫn chưa nghiệm thu; không mặc định đã chơi được trên mọi Android/iOS chỉ từ responsive preview.

## Tiếp tục sau lượt UI 21/10/2026

- UI đã triển khai theo yêu cầu người dùng; đọc [tài liệu 25](25-giao-dien-va-offline.md) trước mở rộng, không bắt đầu lại từ skeleton. Code ở templates/app.html và static/css, js, icons, fonts; không thêm package.json/Node tests hoặc CDN bắt buộc.
- Giữ vỏ HTML công khai không chứa user/CSRF/token, auth/session no-store và API Bearer riêng. Offline giữ access token trong RAM; localStorage chỉ có hint/vùng IndexedDB, không JWT. Đừng đưa tài nguyên/API auth vào Service Worker cache.
- Kết quả UI: 81 test Java/build/Spotless đạt, JS syntax đạt; browser kiểm bố cục/điều hướng/form, menu mobile 390px và cập nhật Service Worker; không tràn ngang/console error ở bản cuối. Ảnh ở build/verification/ui-home.jpg, ui-home-full.jpg, ui-mobile.jpg; JVM/tab preview đã dừng/đóng. PostgreSQL 35 test/prod smoke phía dưới là kết quả trước UI. Không dùng Neon cho nghiệm thu.
- Người dùng chọn chỉ kiểm tra giao diện và test Java sau automatic approval chặn tạo phòng. Không tiếp tục tạo tài khoản/phòng/trận/bản lưu hoặc lách qua API preview. Cần yêu cầu mới của người dùng để chơi/lưu/sync end-to-end bằng browser; hiện không coi các luồng này đã kiểm chứng.
- Việc tiếp theo: nghiệm thu gameplay/Canvas/touch, nhiều tab/hết JWT, mất mạng/reload/save/restore/cloud conflict trên môi trường test; Google thật/HTTPS-WebSocket Render, tải/RAM, Docker/CI và CRUD admin nếu mở rộng. Không tự deploy hoặc commit/push.
- Mọi thay asset/template đổi fingerprint Service Worker khi build; worker mới chờ nút cập nhật khi đã rời trận. Giữ schema 2/save snapshot catalog và revision 0 khi cloud slot thiếu/tombstone.

Các mục sau giữ lịch sử quyết định backend; hướng dẫn “chỉ làm UI khi được yêu cầu” đã được đáp ứng ở lượt này.

## Quyết định hiện tại

- Cập nhật mới nhất: .env và render.env đã đặt BOOTSTRAP_ADMIN_PASSWORD theo mật khẩu người dùng chọn; đã kiểm khớp tuyệt đối, đúng một biến mỗi file và đủ 12 ký tự. Không ghi giá trị ra tài liệu/log. Cần cập nhật Render Environment cùng giá trị rồi Save/redeploy; Git không đồng bộ Dashboard. Bootstrap không reset mật khẩu admin đã có. Không sửa Java, chạy lại build/test hoặc truy cập Neon cho thay đổi giá trị này.

- Log Render mới nhất đã qua Flyway/Hibernate/schema/JDBC, nhưng runner bootstrap dừng do BOOTSTRAP_ADMIN_PASSWORD không đạt 12–64 ký tự khi chưa có admin. .env và render.env tại máy đều có giá trị đạt rule; chưa biết Dashboard đang đặt gì. Người dùng cần cập nhật biến đúng tên trong Render Environment bằng giá trị render.env rồi Save/redeploy; Git không tự đồng bộ env. Không bỏ validation/tắt bootstrap để né lỗi; không đổi key, reset DB hoặc chạy Neon để kiểm. Không sửa Java/env trong lượt chẩn đoán này.

- Đánh giá trước UI: backend cốt lõi đã đủ để tích hợp theo luồng tài khoản → phòng/lobby → Canvas/WebSocket → kết quả/lịch sử/rank → offline/lưu. Chưa viết UI ở lượt đánh giá. Đọc phần đánh giá mới trong 18-backend-va-nghiem-thu.md; browser offline cần engine JS/bot/IndexedDB/Service Worker, API save chỉ đồng bộ khi có mạng. Admin CRUD catalog và Google thật còn giới hạn riêng; không coi test backend là nghiệm thu UI hoặc production.
- Đã sửa chênh lệch P0: khách được GET /api/rankings và /api/rankings/campaign. Không dùng wildcard public /api/rankings/**; /me, tiến độ và phòng vẫn cần JWT. Swagger/test cùng quy định; build offline/Spotless và 79 test Java đạt. PostgreSQL 18.6 có 35 test đạt; prod JAR health/auth/admin login/rooms/migration/schema smoke đạt. Không dùng Neon hoặc kiểm Render/Docker thật; xem STATUS.

- Lượt cho phép commit secrets đã kiểm Git nhận đủ hai PEM, giữ ignore .data/build; 6 SourceContractTest offline, javac GenerateJwtKeys và git diff --check đạt. Không sinh lại/in key, không commit/push, không truy cập DB.

- Yêu cầu mới nhất: cho phép commit toàn bộ secrets/ gồm jwt-private.pem/jwt-public.pem, cùng .env/render.env. .gitignore đã thêm ngoại lệ secrets/** sau các mẫu *.pem/*.key; Docker vẫn loại env/key. Không tự đổi lại chính sách này, không in nội dung key. Render vẫn cần tải hai file lên Secret Files; không tự chuyển runtime sang đọc key từ source hoặc đóng gói key vào image. Người dùng tự commit/push.

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
- Cho phép commit .env/render.env và toàn bộ secrets/ theo yêu cầu chủ đồ án; Docker vẫn loại env/PEM. .data vẫn bị Git bỏ qua, giữ nguyên dữ liệu H2/cặp RSA khi dọn build. Không tự đổi lại chính sách này.
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

UI đã có Thymeleaf/Canvas/engine JS/IndexedDB/Service Worker/reconnect; tiếp tục kiểm browser theo phạm vi người dùng cho phép và tài liệu 25. Trước mở rộng quản trị catalog cần thiết kế API ADMIN, validation/versioning và bảo vệ cấu hình của trận đang chạy.
