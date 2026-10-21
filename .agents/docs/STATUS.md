# Trạng thái ứng dụng

Cập nhật 21/10/2026, Asia/Saigon. Đã triển khai UI; không coi kế hoạch gốc hoặc source hiện có là bằng chứng browser gameplay đã chạy.

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


### Hoàn thiện hình game, hiệu năng, chú thích và kiểm thử xuyên luồng — 21/10/2026

- Thêm game-art.js: vector xe tăng/ba loại địch/năm item, sprite 3x cache hữu hạn và nền bản đồ cache khi đổi map/resize; hai SVG mode card. Renderer chỉ chụp dữ liệu hiển thị, nội suy cả đạn, hạt theo mili giây có trần, tôn trọng reduced motion, dừng vẽ tab ẩn và thu hồi tài nguyên khi dispose. Không đổi luật/va chạm/HP/điểm/seed.
- HUD máu/điểm/mục tiêu, thanh HP địch, legend vật phẩm và nút hướng SVG; giảm cập nhật DOM bằng signature/WeakMap. Vòng solo xuất snapshot/HUD một lần sau các tick bắt kịp, vẫn tick 50 ms và autosave 40 tick. Nút cảm ứng có fallback viewport hẹp; giữ theme/safe area/mobile từ lượt trước.
- Bổ sung chú thích tiếng Việt sát luồng router, snapshot, cache, autosave queue, kết toán local nguyên tử, WebSocket/server authority và đồng bộ revision; bản đồ đọc code trong [27](27-hinh-anh-hieu-nang-va-luong-game.md).
- Tìm và sửa lỗi scope khi server không truy cập được nhưng navigator.onLine còn true: authUnavailable chỉ chọn vùng local; trang dùng IndexedDB đợi authReady để không mở nhầm slot guest trước khi biết userId. Đăng xuất chủ động vẫn về guest; không dùng hint local để xác thực API.
- Người dùng đã cho phép mở rộng kiểm thử H2 tạm. Browser tại 18081: đăng ký/login, tạo phòng/Campaign/EASY, ready/socket/start, di chuyển/bắn, kết quả server/lịch sử; solo Arena/Campaign, pause/autosave/restore/kết quả local; upload revision 1 và tải về; khóa slot hai tab đạt. Sau sửa scope, reload slot 2 của admin đúng tài khoản; dừng JVM và reload qua SW vẫn mở đúng snapshot tài khoản, chơi/lưu local được. Không dùng Neon.
- Viewport 320/390 và 844x390 không tràn ngang; theme sáng/tối, nút hướng portrait 46px và worker update đạt. Console online cuối rỗng; lỗi mạng khi đã tắt server là dự kiến. Ảnh và giới hạn trong tài liệu 27. Không cam kết FPS hoặc mọi Android/iOS chỉ từ preview.
- Build/Spotless full 81 test Java đạt; sau sửa scope chạy lại 24 test auth/source cùng bootJar đạt, không cộng số lượt thành test độc lập. Nghiệm thu cuối chạy spotlessCheck build offline đạt 81 test, 0 failure/error/skipped; XML hiện tại là full suite 81 test. Kiểm cú pháp toàn bộ JS/sw và kiểm tài nguyên precache trong JAR đạt. Chưa chạy lại PostgreSQL/Render cho UI; không đổi Java engine/env/key/migration.
- Người dùng đã yêu cầu tự động commit/push toàn bộ phần UI đang sửa và tắt máy sau commit 10 phút. Quyền mới thay hướng dẫn lịch sử “người dùng tự push/chưa commit” ở các mục cũ. Trạng thái thực tế commit/push được báo bằng Git ở cuối lượt.


### Format và mô tả UI, nền sáng/tối, Android/iOS — 21/10/2026

- Format toàn bộ JS/HTML/CSS, cả template literal chứa HTML và biểu thức dựng trang. Thêm mô tả tiếng Việt sát các hàm/luồng router, auth, IndexedDB, engine, renderer, socket, form/event và Service Worker. Không thêm Node/npm/Prettier vào ứng dụng/CI; formatter tạm ở build bị Git bỏ qua.
- Thêm theme.js chạy trước CSS, nút đổi nhanh topbar và ba lựa chọn Theo thiết bị/Nền sáng/Nền tối trong Hướng dẫn. Nhớ lựa chọn localStorage, đồng bộ tab/hệ điều hành, cập nhật theme-color; đổi nền không dựng lại main/Canvas. Bổ sung palette sáng cho form/card/tag/bảng/dialog; sửa tương phản nhãn hero và bóng menu đã đóng.
- GameControls dùng Map theo pointerId, vừa hướng vừa bắn, trượt đổi hướng, thả/hủy đúng ngón; reset khi blur/ẩn tab/pause/dialog/dispose và hiển thị pressed/aria-pressed. Bổ sung safe area, svh/dvh, nút coarse tối thiểu 44px, input mobile 16px, menu gọn khi xoay ngang, bố cục trận ngang với panel phụ ẩn. Fetch timeout dùng AbortController thay phụ thuộc AbortSignal.timeout, thông báo timeout tiếng Việt; không tự retry mutation do lỗi mạng.
- Nhãn sẵn sàng offline trên trang solo cập nhật khi worker tải đủ, giữ form đang chọn; tiêu đề hẹp không bị ép còn một cột chữ. theme.js được precache và test shell kiểm asset công khai; giữ cache không chứa API/auth/CSRF.
- Đã chạy node --check các module/sw và spotlessApply build offline: 81 test Java, 0 failure/error/skipped. Sau các chỉnh UI nhỏ cuối và tài liệu, chạy spotlessCheck test --tests '*AuthSystemTest' --tests '*SourceContractTest' bootJar đạt 24 test, 0 failure/error/skipped; không cộng thành 105 test độc lập. XML hiện tại ở build/test-results/test là lượt lọc 24 test. JAR/fingerprint đã đóng gói sau thay đổi source cuối.
- Browser preview test H2 in-memory riêng tại 127.0.0.1:18081: xem trang chủ/login/solo setup/hướng dẫn/bản lưu/bảng hạng, desktop 1280, portrait 320/390 và landscape 844x390. Các trang đã đo không tràn ngang; đổi sáng/tối, reload giữ lựa chọn, Theo thiết bị/aria-pressed, menu mobile và kích hoạt worker mới đạt; console error rỗng ở bản cuối. Ảnh build/verification/ui-theme-light.jpg, ui-theme-dark.jpg, ui-mobile-light.jpg, ui-mobile-dark.jpg, ui-landscape-light.jpg. Tab preview đã đóng, viewport đã reset; JVM preview đã dừng. Giữ nguyên tab người dùng ở 18080.
- Theo lựa chọn người dùng chỉ xem UI và test Java: không tạo tài khoản/phòng/trận/save hoặc chơi qua browser/API. Chưa thử nhiều ngón/Canvas gameplay trên Android/Safari iOS thật; chưa kiểm offline gameplay/cloud, parity JS/Java, Google thật hoặc Render HTTPS/WebSocket. Không kết nối Neon, không sửa env/key/migration, không commit/push. Tài liệu mới [26](26-doc-ui-theme-va-mobile.md).


### Giao diện tiếng Việt và solo offline — 21/10/2026

- Thêm vỏ Thymeleaf và layout tối/olive/lime thích ứng desktop/mobile, hero SVG; trang chủ/auth/hồ sơ/phòng/lobby/Canvas/bảng hạng/lịch sử/solo/bản lưu/hướng dẫn. Lucide 0.468.0 và Be Vietnam Pro đóng gói cùng giấy phép, không CDN/npm/frontend build.
- WebPageController chỉ trả vỏ công khai, dữ liệu riêng giữ Bearer API. Thêm GET /auth/session no-store cho CSRF phiên web; form login → session exchange → JWT RAM, refresh cookie HttpOnly, Web Locks/BroadcastChannel. Bổ sung Swagger/Accept-Language; hai test AuthSystemTest kiểm shell/API quyền/CSRF/login/JWT/fingerprint đóng gói.
- Engine JS schema 2 cho solo/bot/Campaign; Canvas/điều khiển keyboard/touch; IndexedDB ba slot theo guest/user, vùng lưu gần nhất khi reload offline, kết toán local nguyên tử, khóa slot đa tab và sync cloud/revision/conflict. WebSocket client dùng ticket/sequence/sync/recovery; không gửi điểm offline lên ranking online.
- Service Worker chỉ precache vỏ công khai/asset/catalog, không API/auth. Gradle tạo fingerprint SHA-256 từ tài nguyên; nút cập nhật yêu cầu rời màn hình trận. Xóa bản cloud đưa metadata local về revision 0, giữ tombstone server.
- Đã chạy node --check cho các module/sw, spotlessApply build offline: 81 test Java, 0 failure/error/skipped. Test Java dùng DB test riêng. Chưa chạy lại PostgreSQL/prod smoke trong lượt UI; các số 35 test PostgreSQL ở mục lịch sử thuộc source trước UI.
- Preview H2 in-memory/test classpath chỉ bind 127.0.0.1:18080; không .env/Neon. Đăng nhập bằng fixture và xem sảnh thành công trước lúc người dùng giới hạn thao tác. Sau automatic approval chặn tạo phòng, người dùng chọn “Chỉ kiểm tra giao diện và test Java”; không tạo phòng/tài khoản/trận/save qua browser hoặc API preview. Đã kiểm trang chủ/auth/solo setup/bảng hạng/lịch sử/bản lưu, desktop mặc định và mobile 390px, không tràn ngang. Menu mobile mở/đóng được; nút cập nhật Service Worker đã chuyển sang UI mới thành công. Console bản cuối không có error. Ảnh: build/verification/ui-home.jpg, ui-home-full.jpg và ui-mobile.jpg. JVM preview đã dừng, tab thử đã đóng. Sau chỉnh CSS sidebar cuối đã đóng gói bootJar đạt; không thêm thay đổi Java sau 81 test.
- Chưa chứng minh parity engine JS/Java, chơi thật/keyboard/touch, offline reload/lưu/khôi phục/cloud conflict, Google thật hoặc HTTPS/WebSocket Render. Không deploy/commit/push. Tài liệu mới [25](25-giao-dien-va-offline.md); giữ nguyên env/key và migration V1–V6.


### Đồng bộ mật khẩu bootstrap admin theo yêu cầu người dùng

- Đã đặt BOOTSTRAP_ADMIN_PASSWORD trong .env và render.env theo mật khẩu người dùng chọn; kiểm tra cả hai file có đúng một biến, giá trị khớp tuyệt đối và đủ 12 ký tự. Không ghi giá trị vào tài liệu/log.
- Người dùng cần cập nhật cùng giá trị trong Render Environment rồi Save/redeploy; thay file local hoặc push Git không tự đồng bộ Dashboard. Bootstrap chỉ dùng mật khẩu khi tạo admin lần đầu, không đổi mật khẩu tài khoản đã tồn tại.
- Chỉ thay giá trị cấu hình, không sửa Java, truy cập Neon hoặc chạy lại build/test. Cần kiểm readiness/đăng nhập trên Render sau khi cập nhật env.

### Log Render mới: bootstrap admin thiếu mật khẩu hợp lệ

- Log người dùng gửi lúc 16:42–16:44 UTC ngày 20/10/2026: prod đã validate đủ sáu migration, schema tank_battle version 6, EntityManagerFactory và Tomcat port 10000 đã khởi tạo. Không còn cảnh báo Flyway PostgreSQL 18 hoặc lỗi relation rooms/app_locks trong đoạn log này. ApplicationRunner sau đó dừng với IllegalStateException: BOOTSTRAP_ADMIN_PASSWORD cần 12–64 ký tự khi tạo admin lần đầu; process exit 1. Không coi dòng Started hoặc Tomcat started là đã readiness hoàn tất.
- Đối chiếu BootstrapAdminInitializer: lỗi này chỉ phát sinh sau khi khóa guard/đọc username thành công, admin cấu hình chưa có và password không đạt ValidationRules. Render đang nhận password thiếu/không hợp lệ; không xác định giá trị Dashboard từ log. Kiểm .env/render.env trên máy bằng metadata/rule độ dài/code point, blank và NUL cho thấy cả hai có password hợp lệ; không in giá trị hoặc sửa mật khẩu.
- Cách xử lý: Render → service → Environment, thêm/cập nhật đúng BOOTSTRAP_ADMIN_PASSWORD bằng giá trị hiện có trong render.env, giữ BOOTSTRAP_ADMIN_ENABLED=true và username đã chọn. Save/redeploy rồi kiểm readiness và đăng nhập admin. Spring/Docker không tự nạp render.env từ Git; commit/push file không tự đồng bộ Dashboard. Giữ nguyên database, schema/migration và cặp RSA.
- Lượt này chỉ chẩn đoán/ghi tài liệu, không thay Java/env, không chạy lại test/build vì không có thay đổi runtime, không đăng nhập/chỉnh Dashboard hoặc truy cập Neon. Kiểm hợp đồng cấu hình local đạt, kiểm git diff --check sau cập nhật tài liệu.

### Rà soát backend trước khi tích hợp UI

- Đã đối chiếu P0/tài liệu nghiệp vụ với API, RoomService/MatchService/OfflineSaveService, RankingRepository, GameCoordinator và bộ test auth/business/engine. Luồng cốt lõi đủ để bắt đầu UI: auth/hồ sơ, phòng, WebSocket/game, kết quả/lịch sử/rank, checkpoint/recovery và đồng bộ save. Chưa coi toàn bộ sản phẩm đã xong khi chưa có UI/browser offline.
- Phát hiện chênh lệch quyền P0: khách được xem bảng hạng nhưng hai API danh sách đang bị bắt JWT. Đã mở đúng GET /api/rankings và GET /api/rankings/campaign bằng matcher theo method/path; giữ route hạng/tiến độ cá nhân và API phòng cần JWT. Cập nhật annotation OpenAPI tương ứng. BusinessFlowTest thêm kiểm quyền công khai/riêng và security của spec.
- Đã chạy spotlessApply build offline: 79 test Java, 0 failure/error/skipped; build/format đạt. Sau đó test-postgres.ps1 -ProductionSmoke đạt 35 test PostgreSQL 18.6, 0 failure/error/skipped; JAR prod mới trên DB trống health UP, auth 401/en, admin login/API quản trị/API phòng và sáu migration trong tank_battle đạt; public không có bảng. Cluster/JVM/key test đã dừng/dọn. Không đổi migration hoặc truy cập Neon, không bắt đầu viết UI.
- Cập nhật tài liệu API/luồng/nghiệm thu và thứ tự UI. Phần còn lại: engine JS/IndexedDB/Service Worker/offline browser, tích hợp nhiều tab, Google thật, admin CRUD catalog nếu đưa vào phạm vi; còn phải kiểm HTTPS/WebSocket Render, tải/RAM và backup/restore trước nghiệm thu.

### Cho phép commit secrets theo yêu cầu chủ đồ án

- Kiểm chứng: Git nhận cả hai PEM ở trạng thái untracked, .data/build vẫn bị ignore; git diff --check đạt. Chạy riêng 6 SourceContractTest offline đạt; javac biên dịch GenerateJwtKeys thành công mà không chạy hoặc tạo key. Không chạy lại bộ DB/Docker vì thay đổi chỉ ở Git/tài liệu/thông báo công cụ.

- Người dùng yêu cầu cho commit đầy đủ secrets sau khi đã được giải thích private key ký JWT. Đã thêm ngoại lệ !secrets/ và !secrets/** sau *.pem/*.key; cho phép toàn bộ thư mục secrets, gồm hai PEM hiện có. PEM/key ngoài secrets vẫn bị bỏ qua; dữ liệu H2/build/cache giữ ignore hiện tại.
- Giữ nguyên nội dung cặp RSA, không in key và không tạo lại. Docker vẫn loại env/key khỏi build context/image; Render vẫn cần thêm hai Secret Files. Chỉ thay chính sách Git, tài liệu và thông báo của GenerateJwtKeys để khớp yêu cầu mới; không commit/push thay người dùng.
- Đã cập nhật quy ước, README và hướng dẫn auth/CI/Neon/Render/Gradle để không còn hướng dẫn hiện hành chặn commit secrets. Các ghi nhận kiểm thử/audit cũ ở dưới là lịch sử trước đổi chính sách.

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

- Đã có UI/Canvas, JS engine, IndexedDB/Service Worker; còn cần nghiệm thu gameplay browser, offline reload/save/restore, nhiều tab và cloud conflict. Người dùng hiện chỉ cho kiểm tra giao diện và test Java.
- Có vai trò/admin bootstrap và API kiểm quyền; catalog gameplay hiện ở JSON, chưa có CRUD API hay màn hình quản trị catalog.
- Google thật cần OAuth Client; chưa thử callback thật. Session web ở RAM, restart cần đăng nhập lại.
- Hiện đã có Git repository; người dùng tự push repo hieuv526034/tank-battle-web. Workflow GitHub/Docker/public Render chưa được chạy thật tại máy này.
- Render URL người dùng cung cấp chưa được xác nhận health public qua công cụ. Env commit không tự cập nhật Dashboard.
- Chưa benchmark tải/query plan/HA hoặc hard-kill đúng thời điểm ghi DB; một runtime instance, chưa ownership xuyên process. Chỉ deploy khi không còn trận RUNNING/RECOVERING và không ai chơi trong lúc phát hành.
- Crash có thể mất đoạn từ checkpoint cuối, khoảng hai giây khi storage khỏe. Test đạt không phải cam kết không còn mọi lỗi.
- Neon từng được kiểm metadata chỉ đọc: PostgreSQL 18.6/public, không chạy Flyway hoặc ghi dữ liệu. Không truy cập lại Neon trong lượt sửa này.

Tiếp tục bằng [HANDOFF](HANDOFF.md), [quy ước](../AGENTS.md), [mục lục](../README.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).
