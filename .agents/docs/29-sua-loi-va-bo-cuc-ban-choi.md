# 29. Sửa lỗi đồng bộ và bố cục bàn chơi

Cập nhật 21/10/2026, tiếp nối [rà soát 28](28-ra-soat-chat-luong-21-10-2026.md).
Yêu cầu: sửa ba lỗi đã xác nhận; header cố định, chỉ nội dung cuộn; menu thu gọn/mở rộng;
bàn chơi vừa màn hình, khóa nền tối; tài khoản chỉ được xem. Không đổi dữ liệu tài khoản production.

## Bản đồ đọc code

| File/điểm vào | Trách nhiệm |
| --- | --- |
| templates/app.html | Header nằm ngoài content-scroll; main/footer cuộn trong khung riêng, body không cuộn. |
| app.js → renderChrome/closeMenu/handleAction | Desktop đầy đủ/rail 80px, nhớ sở thích; mobile drawer khóa nền và giữ focus. |
| game-renderer.js → gameMarkup/mountBattle | Native dialog top layer, nền tối; ResizeObserver tính bản đồ theo hai chiều còn lại. |
| game-renderer.js → GameControls | Nhận phím trong bàn chơi, không nuốt Space trên nút, chặn đầu vào khi xác nhận. |
| api.js → captureIdentity/assertIdentity/api | Thế hệ phiên và chủ JWT bảo vệ request đồng bộ qua await/refresh. |
| app.js → saveContext/checkSaveContext/uploadSave/downloadSave | Một owner từ trước khóa slot tới request và IndexedDB. |
| storage.js → operation/saveGame | Guard sau mở IndexedDB và ngay trước put, đổi phiên thì abort transaction. |
| game-outcome.js → soloOutcome | Cùng quy tắc kết quả cho overlay/lịch sử local, cùng chết tick cuối/bỏ cuộc/Campaign. |
| sw.js → isShellPath/shellNavigation | Chỉ vỏ UI fallback cache khi lỗi mạng/5xx; API/auth/health/Swagger giữ nguyên. |
| auth/controller/ProfileApiController | GET chính chủ; PUT cũ chỉ trả 403/USER_007, không bind body hoặc UPDATE. |
| auth/config/SecurityConfig | API stateless không xóa CSRF sau Bearer GET; POST auth vẫn kiểm token/cookie. Web chain giữ session/CSRF riêng. |

## Bố cục và focus

1. Body/app-shell cao bằng viewport động dvh (fallback vh); workspace gồm header và content-scroll.
   Footer đi theo nội dung. Header/sidebar không dịch chuyển khi cuộn nội dung.
2. Nút menu desktop đổi sidebar đầy đủ ↔ rail 80px, giữ title/aria-label cho icon. Khóa localStorage
   utc-tank-menu-compact chỉ nhớ bố cục, không chứa tài khoản/token. Mobile luôn dùng drawer riêng.
3. Drawer đặt inert cho topbar/nội dung, aria-modal cho sidebar; focus nút đóng, vòng Tab trong sidebar.
   Nút đóng/backdrop/Escape đóng và trả focus về menu. Breakpoint đổi sẽ tháo khóa nền.
4. Vào solo/online: gameMarkup → mountBattle gọi showModal; dialog native tự inert nền, focus Canvas.
   Header bàn chơi có lưu/tạm dừng/rời (solo) hoặc về sảnh (online). Escape chỉ pause solo; online không
   tự đóng hoặc bỏ cuộc. Cụm hướng/bắn tách khỏi điều hướng website.
5. mountBattle đo board-space, trừ HUD/thanh trạng thái; chọn chiều rộng nhỏ hơn giữa chiều rộng có sẵn
   và chiều rộng suy ra từ chiều cao theo tỉ lệ 4:3. Không cắt/méo Canvas/đổi hitbox, không đo trong tick.
6. Portrait đặt cảm ứng dưới bản đồ; landscape thấp đặt bên phải. HUD máu/điểm/mục tiêu vẫn có.
   Trang/bàn chơi không cuộn; kết quả hoặc panel phụ được cuộn khi chữ không đủ chỗ.
7. Hỏi rời solo sẽ pause engine trước khi xác nhận; chọn quay lại thì tiếp tục. Rời trận đợi hàng đợi
   lưu trước khi nhả khóa slot, thu hồi observer/listener/renderer/dialog.
8. Toast nằm trong dialog trận để không chìm sau nền tối. Dialog xác nhận chung nằm ở top layer cao hơn.

## Đồng bộ cloud theo danh tính

- Chụp owner/identity version/route version trước khi đợi Web Lock. Dùng cùng owner cho mọi lần đọc/ghi;
  không chọn chủ mới bằng scope() sau request.
- Thế hệ phiên tăng khi login/logout/đổi chủ token/signed-in từ tab khác. A → B → A vẫn khác thế hệ;
  refresh cùng A không hủy thao tác đang chạy.
- api(..., {identity}) kiểm trước/sau refresh, trước dispatch và sau response; không phát lại mutation
  bằng JWT tài khoản mới. Request đã gửi bằng A có thể đã commit vào A; client không thu hồi được commit
  đó, nhưng không ghi response/revision A sang local B. Lần sync tiếp theo đọc lại revision.
- Upload/download/delete-cloud, tạo trận mới và xóa local đều kiểm context quanh await/xác nhận.
  Guard trong saveGame kiểm cả callback IndexedDB. Autosave lúc cleanup vẫn dùng owner cố định của trận.
- Signed-in từ tab khác luôn nạp lại danh tính dù đang có user. Đổi danh tính đóng socket/dialog riêng tư
  và dựng lại route theo quyền mới. Không lưu JWT vào localStorage hoặc thay kiểm quyền server.

## Kết quả solo và fallback offline

- soloOutcome dùng cho cả overlay và finishGame: bỏ cuộc LOSS; Campaign chỉ WIN nếu còn sống lúc clear;
  Arena MUTUAL_ELIMINATION chỉ DRAW cho người chết đúng tick cuối, chết trước vẫn LOSS; TIME_LIMIT còn
  sống DRAW; LAST_SURVIVOR WIN. Online vẫn lấy kết quả Java; solo không cộng ranking online.
- Worker đọc /offline từ đúng cache phiên bản hiện tại. Fetch reject hoặc response 5xx mới fallback;
  403/404 giữ nguyên. Chưa có cache: giữ response 5xx gốc hoặc trả thông báo 503 rõ ràng khi mất mạng.
- game-outcome.js có trong precache, fingerprint đổi cùng asset/template. Worker mới chờ rời trận rồi
  mới được kích hoạt bằng nút cập nhật, không ép reload giữa trận.

## Hồ sơ bất biến

- /profile dùng văn bản/dl, không có input/form/nút lưu. Không thay tên, username, role hay mật khẩu cũ.
- GET /api/profile trả UserResponse chính chủ. PUT cũ trả USER_PROFILE_READ_ONLY:
  USER_007, key user.profile.read_only, HTTP 403, đủ Việt/Anh; áp dụng cả ADMIN.
- Xóa UpdateProfileRequest, AccountService.rename và UserRepository.rename. Không đổi entity/DDL hoặc
  migration V1–V6. Đăng ký/bootstrap/Google tạo mới vẫn theo validation cũ.
- Test integration gửi displayName/username/role và so DB trước/sau gồm password_hash/updated_at;
  kiểm code/key/locale/no-store. Không chỉ ẩn nút ở browser.

## Kiểm chứng

- Build/Spotless full 83 test Java đạt; PostgreSQL riêng 39 test và prod-profile JAR startup smoke đạt.
  Kết quả cuối, những lần kiểm bổ sung và giới hạn xem [STATUS](STATUS.md).
- Harness tạm build/verification/ui-regression.mjs gọi module/hàm JS thực với fixture RAM: 41 assertion
  đạt cho outcome/IndexedDB, SW, đổi danh tính cloud/refresh/BroadcastChannel và keyboard.
  Không thêm Node/npm/runner frontend vào source hoặc CI. Đây là bằng chứng tạm dưới build, chưa phải
  suite frontend tích hợp. Hợp đồng và cách tái hiện ở đây giúp tiếp tục sau khi dọn build/chuyển máy.
- Preview dùng test classpath/H2 in-memory, chỉ bind 127.0.0.1:18081; không đọc .env hoặc dùng Neon.
  Kiểm header đứng yên khi PageDown, rail 80px và reload nhớ lựa chọn; solo dialog/focus Canvas,
  phím/pause/lưu/rời/chơi tiếp; 1280×720, 390×844, 320×568, 844×390 không tràn bàn chơi,
  nút cảm ứng 44–46px. Mô phỏng viewport không thay điện thoại thật.
- Công cụ screenshot ban đầu lỗi, sau khi mở lại browser đã chụp được. Ảnh dưới build/verification:
  layout-menu-compact.png, layout-battle-desktop.png, layout-battle-mobile.png; build bị Git bỏ qua.
  Kiểm tra tiếp theo và kết quả browser cuối xem STATUS.

Bản cuối đã kiểm lại menu mobile Shift+Tab vòng về Đăng xuất, Escape trả focus về menu, content/header
inert khi mở drawer; hủy rời solo trả focus Canvas; lưu/rời/chơi tiếp giữ đồng hồ/HP; landscape 844×390
không tràn, nút 44px. Sửa màu d-pad/fire trong dialog để theme sáng không làm nền nút sáng với icon lime.
Console error lượt cuối rỗng. Đã reset viewport, đóng tab và dừng JVM preview riêng sau kiểm thử.

## Lỗi CSRF phát hiện trong nghiệm thu online

Sau GET /api/auth/csrf, một GET /api/rooms dùng Bearer làm response xóa cookie XSRF-TOKEN.
UI còn giữ token CSRF cũ trong RAM nên POST /api/auth/socket-ticket bị AUTH_004 và không mở WebSocket.
Đã tái hiện bằng HTTP thật với cookie jar: trước GET cookie/token khớp, sau GET không khớp, xin vé 403.

API chain dùng CookieCsrfTokenRepository và Bearer stateless; chiến lược CSRF session mặc định coi việc
nhận Bearer mỗi request là xác thực mới rồi xóa cookie. Cấu hình sessionAuthenticationStrategy của
CsrfConfigurer riêng API bằng NullAuthenticatedSessionStrategy. CsrfFilter/ApiCsrfFilterPostProcessor
vẫn kiểm mọi POST auth; web chain giữ đổi phiên/CSRF cho form/Google. Không tắt CSRF hoặc bỏ matcher.

AuthSystemTest mô phỏng cookie jar, nhận mọi cookie thay thế từ Bearer GET rồi xin vé: đúng CSRF thành
công, sai CSRF vẫn AUTH_004/403. HTTP thật sau sửa: cookie giữ nguyên, vé SYS_000, token CSRF sai 403.
Browser H2: đăng nhập, tạo phòng, WebSocket connected, ready, Campaign/EASY, bàn chơi online, kết quả
server và về sảnh đạt. Chưa coi một người chơi Campaign là nghiệm thu multiplayer nhiều thiết bị.

## Phần chưa nghiệm thu

Multiplayer 2–4 thiết bị, hard-kill/restart đúng lúc ghi DB, Google callback thật, Safari/Android đa ngón,
tải/FPS/RAM và CRUD catalog quản trị vẫn cần kiểm riêng. Không tự push/deploy/tắt máy từ quyền lịch sử;
Render chỉ nhận source mới sau quy trình Git/CI của người dùng.
