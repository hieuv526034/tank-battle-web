# 27. Hình ảnh trận đấu, hiệu năng và luồng cần đọc

Cập nhật 21/10/2026. Tài liệu này bổ sung 25–26; mô tả source và bằng chứng kiểm thử, không cam kết mọi thiết bị đều đạt cùng FPS.

## Bản đồ đọc code

| Điểm vào | Luồng và quy tắc nghiệp vụ |
| --- | --- |
| `templates/app.html` | Vỏ công khai; theme trước CSS; main/dialog/toasts là vùng router quản lý. Không nhúng user/JWT/CSRF vào trang cache. |
| `js/app.js` → `renderRoute` | Tăng routeVersion → thu hồi màn hình cũ → đợi auth cho trang cần danh tính/vùng local → nạp dữ liệu → kiểm alive sau await → dựng DOM. Phản hồi trang cũ không được ghi đè trang mới. |
| `js/api.js` → `initializeAuth` | Phân biệt phiên hết hạn với server không truy cập được. `authUnavailable` chỉ chọn dữ liệu local, không cấp quyền API. Access JWT trong RAM, refresh cookie HttpOnly và CSRF giữ nguyên. |
| `js/storage.js` → `scopeFor` | Có user dùng userId; lỗi kết nối dùng vùng gần nhất; đăng xuất chủ động đặt guest. Dữ liệu local thuộc hồ sơ trình duyệt, không phải ranh giới bảo mật giữa người dùng chung máy. |
| `js/app.js` → `soloGame` | Khóa slot đa tab → restore schema/catalog → tick 50 ms → xuất snapshot/HUD → autosave tuần tự → finish. Rời/ẩn tab reset input; rời màn hình chờ lưu rồi giải phóng khóa. |
| `js/offline-engine.js` → `step` | Hết buff → người chơi → địch → đạn → nhặt item → wave/kết thúc. Engine quyết định va chạm/HP/điểm; renderer không được sửa các trường này. |
| `js/storage.js` → `finishGame` | Một transaction ghi kết quả theo scope:matchId, mở cửa theo độ khó và xóa save dở; abort giữ save. Không gửi kết quả solo lên ranking online. |
| `js/online.js` → `RoomConnection` | JWT → vé một lần → WebSocket cùng origin; sequence tăng dần, reconnect xin vé mới và sync. Không phát lại input cũ hoặc tự khai điểm/vị trí. |
| `js/app.js` → `onlineGame` | Chỉ đọc frame đúng matchId. RECOVERING cần xác nhận; SAVING/ENDING reset điều khiển; FINISHED lấy kết quả chính thức từ API. |
| `js/app.js` → `uploadSave/downloadSave` | Gửi expectedRevision; xung đột phải chọn bản giữ. Tải về kiểm schema rồi mới ghi. Khóa slot ngăn đồng bộ đè trận đang chơi. |
| `js/game-renderer.js` | Snapshot hiển thị tách state mô phỏng; nội suy có chặn, không dự đoán gameplay. GameControls hợp nhất bàn phím và từng pointer riêng. |
| `js/game-art.js` | Vector xe/địch/item và địa hình thuần hình ảnh, không input/DB/RNG seed. Sprite cache số biến thể hữu hạn. |
| `sw.js` | Chỉ precache vỏ/asset/catalog công khai; không cache API/auth/token. Worker mới chờ người chơi rời trận và chọn cập nhật. |

Các hàm trên có chú thích tiếng Việt sát code. Khi sửa nghiệp vụ phải cập nhật cả chú thích và kiểm chứng, không chỉ ghi phần giới thiệu module.

## Tối ưu vòng vẽ

- Địa hình được vẽ vào Canvas rời khi đổi rules/resize; mỗi frame chỉ drawImage lớp nền. Không dùng OffscreenCanvas/worker bắt buộc.
- Sprite vector raster ở 3x, cache hữu hạn cho tank/màu, ba loại địch và năm item. Hai hình mode card là SVG tự viết, không thêm thư viện/CDN.
- Snapshot hiển thị chỉ copy trường động cần dùng; không structuredClone toàn bộ content/stage mỗi tick. Rules được so giá trị và clone khi đổi, nên object rules mới từ mạng không làm vẽ lại nền liên tục.
- Nội suy vị trí xe/địch/đạn giữa hai snapshot, chặn hệ số 0–1. Không suy đoán vị trí sau snapshot mới nhất, đổi hitbox hoặc can thiệp kết quả.
- Hạt hiệu ứng có trần 160, sống theo mili giây và dùng RNG trang trí riêng. Đạn dùng vệt/lõi đơn giản, không shadowBlur từng viên. Tôn trọng prefers-reduced-motion.
- Tab ẩn dừng rAF hiển thị, tab hiện đặt lại mốc và bỏ hạt cũ. Snapshot đứng yên quá 200 ms giảm vẽ xuống 10 Hz. Dispose giải phóng observer/listener/rAF/Canvas nền.
- DPR tối đa 3 nhưng giới hạn 2,4 triệu pixel mỗi Canvas nền/màn hình. Hai lớp cực đại khoảng 19,2 MB pixel, chưa gồm bộ nhớ trình duyệt; đây là trần tính toán, chưa phải số RAM đo trên điện thoại.
- HUD chỉ thay DOM khi giá trị đổi. WeakMap gắn cache với element để router không giữ DOM cũ. Đồng hồ/buff đổi theo giây; roster đổi khi HP/tên/kill/score thay đổi.
- Solo giữ tick 50 ms, chỉ xuất renderer/HUD một lần sau các tick bắt kịp trong cùng frame; autosave vẫn mỗi 40 tick.

## Mobile và hình ảnh

HUD máu/điểm/mục tiêu nằm ngay dưới Canvas nên vẫn thấy khi panel phụ ẩn. Hình item không dùng emoji/glyph hệ điều hành; nút hướng dùng Lucide SVG. Nút cảm ứng hiện trên coarse pointer hoặc viewport hẹp làm dự phòng. Safe area, svh/dvh, kích thước chạm 44 px trở lên và theme sáng/tối giữ từ tài liệu 26. Không khóa hướng màn hình.

## Lỗi tìm thấy và sửa khi kiểm thử

Khi Wi-Fi còn kết nối nhưng server không phản hồi, navigator.onLine vẫn true. Cách chọn scope cũ trả guest và có thể mở nhầm slot. Ngoài ra URL chơi tiếp được xử lý trước khi auth xác định user, nên tải trực tiếp có thể đọc guest trước.

Đã sửa: API đánh dấu trạng thái không truy cập được riêng với 401; trang offline/saves/history đợi authReady trước khi chọn vùng local. Chỉ mở scope gần nhất trong tình huống local-only; đăng xuất chủ động vẫn xóa lựa chọn tài khoản. Cờ local-only không giả lập đăng nhập và không làm API chấp nhận quyền.

## Bằng chứng và giới hạn

- Preview H2 in-memory test classpath, chỉ bind 127.0.0.1:18081. Người dùng đã xác nhận mở rộng kiểm thử tạo tài khoản/phòng/chơi/save trên H2 tạm. Không truy cập Neon/production hoặc thay env/key/migration.
- Browser: đăng ký/đăng nhập; tạo phòng, đổi Campaign/EASY, ready/socket, bắt đầu, giữ phím di chuyển/bắn, server kết toán và lịch sử; solo Arena/Campaign, pause/resume/autosave và kết quả local; upload revision 1 và tải snapshot từ H2 về; tab thứ hai bị chặn mở cùng slot.
- Regression sau sửa scope: tài khoản fixture admin tạo slot 2, reload URL chơi tiếp vẫn giữ tài khoản; dừng JVM server, reload từ Service Worker vẫn phục hồi đúng slot/tên/HP của tài khoản, tiếp tục vẽ/chơi/lưu local. Đây là thử server không truy cập được khi thiết bị vẫn online, không giả vờ đã thử chế độ máy bay thật.
- Viewport 320/390 và 844x390 không tràn ngang, nút hướng đo 46 px ở portrait; theme sáng/tối, HUD và worker update hoạt động. Console online cuối rỗng; khi server đã dừng có lỗi mạng dự kiến, không dùng chúng làm bằng chứng JS crash.
- Ảnh ở `build/verification/ui-arena-polished.jpg`, `ui-game-mobile-final.jpg`, `ui-game-landscape.jpg`; build bị Git bỏ qua. Preview JVM đã dừng, các tab thử được đóng và viewport reset sau nghiệm thu.
- Chưa đo FPS/RAM trên Android/iPhone thật, chưa thử đa ngón thật, mất mạng WebSocket giữa trận/recovery restart/multiplayer nhiều người, cloud conflict nhiều thiết bị, parity JS/Java, Google thật hoặc HTTPS/Render. Bộ test backend không thay thế các lượt nghiệm thu này. Kết quả build/test cuối xem STATUS.
