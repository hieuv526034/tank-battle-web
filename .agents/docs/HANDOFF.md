# Bàn giao tiếp tục source

Cập nhật 21/10/2026. Đọc [quy ước](../AGENTS.md), [STATUS](STATUS.md), [luồng backend](20-luong-backend-va-test.md) và [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).

## Tiếp tục sau sửa màu nút 21/10/2026

- Đọc [31](31-trang-thai-nut-va-theme.md): disabled nghiệp vụ dùng palette rõ chữ, busy dùng disabled + aria-busy giữ màu/vòng chờ. Không thêm opacity thấp trở lại cho mọi button:disabled. Dùng ui.setButtonBusy và finally trong click/submit; không bỏ khóa request hoặc điều kiện nghiệp vụ.
- Hero/battlefield/battle-dialog giữ bảng màu tối, baseline/hover nút primary chung cuối CSS. Kiểm theme sáng khi sửa selector: quy tắc :root[data-theme=light] từng có độ ưu tiên cao hơn selector vùng game.
- Chromium riêng/static source thật/API giả đã kiểm trạng thái nút, form login lỗi/chống lặp, theme/hủy dialog và pageerror. Harness/ảnh dưới build/verification bị ignore, không cần Node/npm cho ứng dụng. Kết quả Gradle mới đọc STATUS; không lấy 86/39 của lượt trước làm số test chạy lại lượt UI này.
- Người dùng đã commit lượt trước thành df3f8b8; chỉ giữ các thay đổi màu nút và tài liệu hiện tại, không tự commit/push/deploy hoặc mở app bằng env Neon. Nếu còn thấy UI cũ, rời trận rồi cập nhật Service Worker.

## Tiếp tục sau rà soát trước commit 21/10/2026

- Người dùng hỏi còn vấn đề gì; đã rà source/CI/report, không chạy lại test hoặc full app. Báo cáo hiện có vẫn 86 test Java + 39 PostgreSQL đạt từ lượt trước. Đã sửa thông tin UI/browser cũ trong tài liệu 20/22 và mục giới hạn STATUS. Không có sửa logic/env/key/DB trong lượt này.
- Ưu tiên nghiệm thu 2–4 người/reconnect rồi kill/restart JVM thật và kiểm matchId/deadline/kết quả/ranking không nhân đôi. Test tạo coordinator mới chưa chứng minh crash đúng lúc commit. Tiếp theo CI/Docker/Render của commit mới, Google callback thật, nhiều thiết bị/mobile, Java-JS parity và tải/backup-restore.
- Giới hạn cần nói rõ: một instance, chưa ownership/fencing; không deploy khi đang có trận. Catalog chỉ cấu hình JSON/restart, chưa CRUD admin; harness JS tạm 41 assertion chưa vào CI. Không coi thiếu nghiệm thu là lỗi đã được tái hiện, cũng không tuyên bố không còn lỗi.
- Git vẫn d725c22 với working tree thay đổi chưa commit và .run/game-outcome.js untracked. Nếu người dùng tự commit, cần stage cả file mới; nội dung commit đã được cung cấp ở chat. Không tự commit/push/deploy từ yêu cầu hỏi tình trạng.

## Tiếp tục sau lỗi Flyway local prod lặp lại 21/10/2026

- Đọc [30](30-chay-local-va-nap-env.md). Đã thêm cấu hình IntelliJ dùng chung .run/TankBattleApplication-prod-local.run.xml, tên TankBattleApplication (prod local), prod và working directory $PROJECT_DIR$. Cấu hình cũ trong .idea/workspace.xml cũng được sửa nhưng bị ignore/IDE có thể giữ RAM; hướng dẫn người dùng chọn cấu hình chia sẻ mới. Không tự đổi application-prod.yml sang H2 hoặc nhét URL/password vào YAML.
- JVM ConfigData không auto-config/DB đã chứng minh root + prod tường minh/mặc định đọc .env được; cwd build/classes/java/main gây thiếu biến. Chưa thu được cwd của chính lần Run IDE bị lỗi, nên không khẳng định chắc chắn nguyên nhân duy nhất. Không chạy full app với env Neon để chứng minh.
- Unit test import dùng prod tường minh; full build/Spotless 86 test đạt. tools/test-production-startup.ps1 nay dùng .env mặc định trong runRoot tạm, xóa APP_ENV_FILE cùng DB/JWT khỏi env con. PostgreSQL riêng 39 test và JAR prod/Flyway V1–V6/health/auth/admin/rooms đều đạt; fixture/JVM/cluster đã dọn. Không quay về chỉ kiểm APP_ENV_FILE rồi kết luận default path đã được kiểm.
- Giữ các thay đổi UI/auth/env từ lượt trước. Không đổi DB/PEM/mật khẩu, không commit/push/deploy hoặc tắt app người dùng trong lượt sửa này.

## Tiếp tục sau sửa chạy local 21/10/2026

- Đọc [30](30-chay-local-va-nap-env.md). Prod tự import .env hoặc APP_ENV_FILE qua ConfigData; đây vẫn là prod/PostgreSQL, không thêm H2/local runtime. IDE Working directory phải là root/JDK 21; biến Run Configuration thật thắng file.
- .env đã thêm issuer localhost, hai JWT resource file:./secrets/... và hai cờ cookie HTTP false; giữ DB/password/key hiện có. Không đổi render.env/Docker/HTTPS mặc định; không sinh lại RSA. Không quay lại mô tả “Spring không tự đọc .env” cho source hiện tại.
- JUnit dùng fixture và test profile không import .env. ProductionProfileTest base trỏ file không tồn tại để không đọc env thật. tools/test-production-startup.ps1 dùng APP_ENV_FILE/startup.env tạm, bỏ DB/JWT khỏi env của JAR con và dọn trong finally; không dùng Neon.
- Full 86 test Java/build/Spotless, 39 test PostgreSQL và JAR smoke tự import startup.env đạt. Xóa entry env trong PowerShell bằng Remove-Item Env:, không SetEnvironmentVariable($null) vì có thể thành chuỗi rỗng thắng file. Fixture/JVM/cluster đã dọn; JAR không nhúng env/key. Không tự bootRun với .env thật để kiểm vì sẽ chạy Flyway trên production. Chưa commit/push/deploy.

## Tiếp tục sau sửa lỗi và bố cục bàn chơi 21/10/2026

- Đọc [29](29-sua-loi-va-bo-cuc-ban-choi.md). Ba lỗi review đã sửa: context owner/identity/route từ trước khóa slot và guard trong API/IndexedDB; soloOutcome dùng chung; navigation chỉ vỏ UI fallback mạng/5xx trong đúng cache worker.
- Giữ header ngoài content-scroll, body không cuộn; desktop rail 80px và sở thích localStorage; mobile drawer inert/focus/Tab/Escape. Bàn chơi là native dialog top layer, ResizeObserver tính bản đồ 4:3 theo cả hai chiều, không đo trong tick. Không dùng selector dialog[open] chung để chặn game vì chính bàn chơi cũng là dialog; chỉ #modal[open] chặn input.
- Hồ sơ sau tạo chỉ xem; không thêm lại form/updateProfile/rename. GET /api/profile chính chủ; PUT cũ luôn USER_007/403 vi/en cho USER và ADMIN. Không reset thông tin tài khoản, không đổi migration V1–V6/env/key.
- Sửa thêm CSRF session strategy riêng API stateless: Bearer GET không được xóa cookie CSRF mỗi request; web chain giữ strategy riêng, CsrfFilter vẫn kiểm POST auth. Test cookie jar/HTTP thật và browser phòng/socket/Campaign online đạt; không tắt CSRF để né lỗi.
- Kiểm chứng: full 83 test Java/build/Spotless, 39 PostgreSQL và prod-profile JAR startup smoke đạt; harness JS tạm 41 assertion, syntax JS/sw đạt. Browser dùng H2 riêng; xem STATUS cho lượt cuối/ảnh/giới hạn thiết bị. Harness/formatter/classpath preview dưới build bị ignore, không là dependency ứng dụng/CI.
- Còn nhiều thiết bị/reconnect/restart cứng/Google/điện thoại thật/tải. Không dùng quyền commit/push/tắt máy ở mục lịch sử cho lượt này. Không truy cập Neon để test.
- Bản cuối: preview H2 18081 đã dừng, tab đã đóng/viewport reset. Ảnh nằm trong build/verification; menu mobile focus/inert và solo save/restore/online kết quả đạt. Không coi bản Render hiện tại đã có các thay đổi này khi chưa push/redeploy.

## Tiếp tục sau rà soát chất lượng web 21/10/2026

Mục dưới giữ lịch sử review trước sửa. Source mới và kết quả hiện tại đọc mục trước và STATUS.

- Đọc [28](28-ra-soat-chat-luong-21-10-2026.md). Đã tái hiện ba lỗi JS nhưng chưa sửa: downloadSave chụp scope sau await nên có thể ghi nhầm tài khoản; solo MUTUAL_ELIMINATION bị LOSS thay DRAW; SW navigation không fallback cached shell khi fetch trả 5xx.
- Ưu tiên chụp owner/generation và kiểm lại danh tính quanh cloud sync; thống nhất outcome giữa UI/storage; xử lý 5xx cho navigation công khai mà không cache API/auth. Harness RAM ở build/verification/review-cases.mjs không phải test suite frontend đã tích hợp.
- Kết quả lượt này: 81 Java/build/Spotless, 37 PostgreSQL, prod-profile JAR smoke đạt. Public Render home/readiness UP/bảng hạng công khai đạt qua browser; không ghi dữ liệu production. Chưa nghiệm thu nhiều người/recovery restart/cloud conflict/Google/điện thoại thật hoặc Docker/CI.
- Chỉ cập nhật tài liệu đánh giá, không đổi logic/env/key/migration, không commit/push. Không áp dụng lại yêu cầu tắt máy/commit của lượt hình ảnh trước cho lượt review này.

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
