# 25. Giao diện, Canvas và solo offline

Cập nhật 21/10/2026. Đọc [STATUS](STATUS.md) để phân biệt tính năng trong source với bằng chứng chạy. Người dùng chọn chỉ kiểm tra giao diện và test Java; không tạo phòng/tài khoản/trận/bản lưu bằng trình duyệt trong lượt nghiệm thu này.

## Stack và bố cục

Thymeleaf trả `templates/app.html`, ES modules ở `static/js`, CSS riêng và Canvas 2D. Giao diện tiếng Việt theo chủ đề trạm chỉ huy, nền tối/olive và màu lime, sidebar desktop/menu mobile, trạng thái tải/trống/lỗi, dialog và thông báo. Không có npm/package.json hoặc bước build frontend riêng.

Lucide SVG 0.468.0 và Be Vietnam Pro Regular/Bold nằm trong source cùng giấy phép, không cần CDN khi online/offline. Nguồn và giấy phép đọc [THIRD_PARTY_NOTICES](../../THIRD_PARTY_NOTICES.md). Tank và cảnh hero là SVG tự viết.

| Trang | Nội dung |
| --- | --- |
| `/` | Trạm chỉ huy, chọn online/solo, bảng hạng công khai |
| `/login`, `/register` | Form, lỗi field, hiện/ẩn mật khẩu; Google chỉ hiện khi đã cấu hình |
| `/rooms`, `/rooms/{code}` | Danh sách/tìm phòng, tạo/tham gia, presence/ready và cấu hình chủ phòng |
| `/play/{id}` | Canvas/HUD online, kết nối lại, recovery, kết quả chính thức |
| `/leaderboard` | Arena/Campaign theo độ khó, phân trang và hạng cá nhân |
| `/history` | Lịch sử online riêng tư hoặc tối đa 20 kết quả solo trên máy |
| `/profile` | Hồ sơ, đổi tên hiển thị, đăng xuất phiên API |
| `/offline` | Chọn Arena/bot hoặc Campaign/cửa/độ khó/vị trí lưu |
| `/saves` | Ba slot trên máy/đám mây, chơi tiếp, tải lên/về, xóa và xử lý xung đột |
| `/help` | Điều khiển, luật, lưu/khôi phục và kiểm tra tài nguyên offline |

## Auth và dữ liệu

WebPageController chỉ trả vỏ HTML công khai, không nhúng danh tính/CSRF/JWT. Mọi dữ liệu riêng vẫn qua API Bearer, `/api/admin/**` giữ ADMIN. GET `/auth/session` trả authenticated/googleEnabled/csrfHeaderName/csrfToken với Cache-Control no-store; CSRF phiên web dùng cho POST `/login`, `/logout`, `/auth/token`. API cookie vẫn có CSRF riêng `/api/auth/csrf`.

`api.js` giữ access JWT trong RAM; refresh nằm trong cookie HttpOnly. Web Locks tuần tự hóa login/refresh/logout giữa các tab; tab thiếu Web Locks dùng session exchange để tránh rotate đồng thời. BroadcastChannel báo thay đổi đăng nhập. Không retry mutation sau lỗi mạng không xác định kết quả; chỉ thử lại một lần khi API trả 401. Logout UI thu hồi phiên API rồi đóng phiên web hiện tại; không khẳng định đóng mọi HttpSession ở thiết bị khác.

`storage.js` dùng IndexedDB theo guest hoặc userId. Chỉ nhớ khóa vùng lưu gần nhất trong localStorage để mở đúng bản solo khi tải lại lúc offline; khóa này không phải xác thực/quyền gọi API. Đăng xuất chuyển về vùng guest. Bản guest không tự chuyển vào tài khoản. Dữ liệu local không được mã hóa và thuộc hồ sơ trình duyệt; xóa dữ liệu website sẽ mất bản trên máy.

## Gameplay và lưu

`game-renderer.js` vẽ bản đồ/tank/đạn/địch/item và nội suy snapshot. WASD/phím mũi tên di chuyển, Space/J bắn, P/Escape tạm dừng solo; cảm ứng dùng cụm hướng và nút bắn. Trận online do Java quyết định; UI chỉ gửi intent 50 ms, không tự ghi điểm online.

`online.js` dùng opaque ticket một lần, reset sequence khi nối lại, sync state thật sau mất mạng/refresh JWT. Socket bị đóng do tab khác chiếm kết nối không tự tranh lại liên tục; người chơi có nút kết nối lại. RECOVERING hiển thị deadline/xác nhận; SAVING/ENDING chờ máy chủ và chỉ hiển thị kết quả chính thức sau kết toán.

`offline-engine.js` triển khai Arena/bot, Campaign/wave, địch/items/buffs và schema 2, lấy snapshot catalog JSON được cache. Tick cố định 50 ms, PRNG lưu seed, va chạm đoạn đạn. Source dùng luật/schema tương ứng Java; chưa có kiểm thử so sánh engine JavaScript với Java hoặc chơi browser xuyên suốt, không coi là đã chứng minh tương đương hoàn toàn.

Solo tự lưu mỗi hai giây, tạm dừng khi ẩn tab, lưu trước khi rời màn hình. Web Locks ngăn hai tab chơi/ghi cùng slot; trình duyệt thiếu Web Locks không bảo đảm tránh ghi đè giữa tab. Kết quả solo, cập nhật tiến độ cửa và xóa save dở cùng transaction IndexedDB. Solo không gọi API kết toán hoặc cộng bảng hạng online.

Đám mây chỉ đồng bộ khi người dùng chọn. PUT gửi expectedRevision; thiếu/tombstone dùng 0, cập nhật dùng revision đã đọc. SAVE_002 mở lựa chọn giữ bản trên máy hoặc bản đám mây. Xóa đám mây không xóa bản local; server giữ tombstone để tăng revision. Tải về kiểm schema trước khi thay bản trên máy. Upload/download/delete dùng khóa slot để tránh ghi đè trận đang chơi ở tab khác.

## Service Worker và phát hành

`sw.js` chỉ precache danh sách asset cố định và vỏ `/offline`; không cache API/auth/OAuth/WebSocket. Vỏ trang không có dữ liệu cá nhân. Hiện “Đã sẵn sàng offline” sau khi kiểm đủ tài nguyên. Offline cần HTTPS hoặc loopback và lần tải tài nguyên thành công trước đó.

Gradle processResources tính SHA-256 từ static/template để thay `@UI_REVISION@` trong sw.js. Thay asset đổi cache tự động, không tăng version bằng tay. Worker mới chờ; nút cập nhật ở topbar yêu cầu rời màn hình trận trước khi kích hoạt và reload. Cache bản cũ được dọn khi worker mới activate. Không force reload giữa trận.

## Kiểm chứng và phần cần nghiệm thu

- Build/Spotless, test Java AuthSystemTest gồm vỏ HTML công khai nhưng API riêng 401, form CSRF → session → JWT, không cache CSRF, fingerprint worker được thay khi đóng gói. Kết quả cuối trong STATUS.
- JavaScript đã kiểm cú pháp bằng node --check tạm; không thêm Node vào project/CI hoặc bộ test Node thường trực.
- Preview dùng test classpath/H2 in-memory, loopback 127.0.0.1:18080; không .env/Neon. Đã kiểm hình thức/điều hướng desktop và mobile 390px, menu mở/đóng, thay asset qua nút cập nhật Service Worker; không tràn ngang, console bản cuối không có error. Không tạo phòng/trận/save qua UI theo lựa chọn người dùng. Ảnh ở build/verification/ui-home.jpg, ui-home-full.jpg và ui-mobile.jpg. Preview đã dừng sau kiểm tra. Bộ test Java vẫn tạo fixture trên DB test riêng.
- Cần nghiệm thu gameplay/touch, nhiều tab/hết token/reconnect, offline reload/lưu/chơi tiếp/cloud conflict bằng browser. Google thật, HTTPS/WebSocket Render, tải/RAM, deploy Docker và quản trị CRUD catalog chưa được xác nhận.