# Đọc code UI, nền sáng/tối và điện thoại

Cập nhật 21/10/2026. Đọc cùng [UI/offline](25-giao-dien-va-offline.md) và [STATUS](STATUS.md).

Bố cục/focus hiện tại dùng header ngoài vùng cuộn và native dialog cho bàn chơi; đọc [29](29-sua-loi-va-bo-cuc-ban-choi.md).
Màu nút disabled/đang xử lý, helper setButtonBusy và kiểm browser mới đọc [31](31-trang-thai-nut-va-theme.md).

## Bản đồ file và thứ tự đọc

| File trong src/main/resources | Vai trò và điểm vào |
| --- | --- |
| templates/app.html | Vỏ công khai: sidebar, topbar, main, footer, toast và dialog dùng chung. Không nhúng tài khoản/CSRF/JWT. |
| static/js/theme.js | Chạy đồng bộ trong head trước CSS; chọn nền trước khi vẽ trang. |
| static/js/app.js | Router renderRoute, trang, submitForm, handleAction; quản lý vòng đời trận và worker. |
| static/js/api.js | CSRF, session web, JWT trong RAM, refresh, lỗi API và timeout. |
| static/js/ui.js | Escape dữ liệu, icon/avatar, loading, thông báo, dialog và validation form. |
| static/js/storage.js | IndexedDB: saves, results, meta; tách vùng guest/user và transaction. |
| static/js/game-outcome.js | Quy tắc kết quả solo dùng chung cho overlay và lịch sử IndexedDB. |
| static/js/offline-engine.js | Snapshot schema 2, RNG, tick, bot/địch, đạn, vật phẩm, wave và kết thúc. |
| static/js/game-renderer.js | GameRenderer đọc state; GameControls hợp nhất keyboard/pointer; HUD/khung trận. |
| static/js/online.js | RoomConnection: vé socket, sequence, sự kiện phòng/trận, sync và reconnect. |
| static/sw.js | Precache vỏ/asset/catalog, bỏ cache cũ và kích hoạt cập nhật theo thao tác người chơi. |
| static/css/app.css | Font/token → layout → thành phần → trận → breakpoint → theme sáng → Android/iOS. |

Các hàm chính có mô tả tiếng Việt sát khai báo. Mô tả luồng không thay thế việc đọc caller, cleanup và điều kiện lỗi.

## Luồng mở trang và chuyển màn hình

1. Trình duyệt nhận app.html. theme.js đọc sở thích, đặt data-theme và theme-color trước CSS.
2. app.js khởi động kiểm phiên và dựng vỏ song song. Trang riêng chờ authReady; solo công khai không cần tài khoản.
3. renderRoute tăng routeVersion, chạy cleanups của màn hình cũ rồi chọn hàm dựng trang theo URL.
4. Request có thể hoàn thành sau khi đã rời trang. alive/setContent chỉ gắn nội dung khi version còn khớp.
5. Link data-link đi qua navigate; form data-form qua submitForm; nút data-action qua handleAction. Dùng event delegation vì nội dung main thay đổi.
6. Màn hình trận đăng ký thu hồi renderer/controls/timer/listener. Rời solo thì chờ hàng đợi lưu trước khi nhả khóa slot.
7. prepareOffline hỏi worker đã có đủ tài nguyên chưa; cập nhật nhãn sidebar và nhãn trang solo mà không dựng lại form đang nhập.

Dữ liệu động đưa vào innerHTML phải qua esc. HTML action nội bộ được dựng bằng helper; không đưa trực tiếp thông báo/HTML chưa chuẩn hóa từ server vào trang.

## Luồng tài khoản và request

- webSession lấy CSRF no-store; đăng nhập form tạo session web; exchange đổi session thành JWT để gọi API riêng.
- Access token chỉ ở RAM. Refresh cookie là HttpOnly. localStorage chỉ giữ hint/vùng lưu/sở thích, không có JWT.
- refresh dùng Web Locks giữa các tab; trình duyệt thiếu khóa dùng session exchange. BroadcastChannel đồng bộ trạng thái đăng nhập/đăng xuất.
- fetchWithTimeout dùng AbortController/timer 15 giây, không phụ thuộc AbortSignal.timeout. Luôn gỡ listener/timer và giữ quyền hủy của caller.
- Chỉ thử lại API sau 401 đã refresh. Lỗi mạng không phát lại mutation vì chưa biết server đã commit hay chưa.

## Luồng nền sáng/tối

- Nút mặt trời/mặt trăng trên topbar đổi nhanh giữa sáng và tối.
- Trang Hướng dẫn có ba lựa chọn: Theo thiết bị, Nền sáng, Nền tối.
- Khóa localStorage utc-tank-theme nhận system/light/dark; mặc định system. Khi storage bị chặn, vẫn đổi được trong phiên.
- app.js phát theme-preference. theme.js kiểm giá trị, đặt data-theme, data-theme-preference, theme-color rồi phát themechange.
- app.js chỉ dựng lại chrome và trạng thái aria-pressed của nút theme. Không dựng lại main/Canvas hoặc khởi động lại trận khi đổi nền.
- Khi chọn system, media query theo hệ điều hành được áp dụng. storage event đồng bộ lựa chọn giữa các tab.
- Theme sáng ghi đè token/bề mặt chữ, form, tag, bảng, thông báo và dialog. Hero/chiến trường giữ nền quân sự tối với chữ/đạn tương phản.

## Luồng cảm ứng Android/iOS

GameControls không thay đổi luật hoặc state gameplay; nó chỉ trả direction/fire cho engine solo hoặc client socket.

1. pointerdown ghi pointerId vào Map, phân biệt d-pad và nút bắn rồi capture pointer.
2. Mỗi ngón có trạng thái riêng. Giữ hướng và bắn đồng thời không ghi đè nhau.
3. Khi trượt trên d-pad, pointermove dò elementFromPoint để đổi hướng; trượt ra ngoài d-pad dừng hướng của ngón đó.
4. reconcile ưu tiên ngón hướng còn hiệu lực mới nhất, sau đó phím mới nhất. Có bất kỳ ngón bắn hoặc Space/J thì fire=true.
5. pointerup/pointercancel/lostpointercapture chỉ bỏ đúng pointerId. Nút nhận pressed và aria-pressed tương ứng.
6. Blur, ẩn tab, pause, dialog chặn đầu vào hoặc dispose xóa trạng thái để tránh kẹt bắn/chạy.

CSS touch-action:none chỉ áp dụng cụm điều khiển/Canvas; không cấm zoom toàn website. Nút cảm ứng tối thiểu 44px, d-pad thường 46px. any-pointer:coarse giữ cụm nút khi thiết bị cảm ứng có thêm chuột/bàn phím.

- Portrait hẹp: nội dung một cột, tiêu đề xuống dòng, nút lớn và input 16px.
- Landscape thấp: menu thu gọn, bản đồ và cụm nút nằm cạnh nhau; ẩn panel phụ để dành chỗ cho trận.
- Dùng safe-area-inset quanh tai thỏ/home indicator; svh/dvh theo vùng nhìn thấy khi thanh địa chỉ thay đổi, có khai báo vh trước làm fallback.
- Giới hạn pixel ratio Canvas ở 2 để giảm lượng pixel/bộ nhớ trên điện thoại. Không khóa hướng màn hình và không bắt cài app.
- Dùng trình duyệt Android/iOS hiện đại. HTTPS cần cho Service Worker và các API trình duyệt tương ứng; localhost là môi trường preview riêng. Không coi kiểm kích thước desktop là bằng chứng đã thử Safari/iPhone thật.

## Quy ước format

- JS/HTML/CSS dùng 2 dấu cách, UTF-8, xuống dòng LF theo .editorconfig; tách khối và giữ dòng khoảng 100 ký tự khi hợp lý.
- Template literal chứa HTML cũng được xuống dòng/thụt lề; biểu thức map/ternary bên trong được tách cho dễ theo dõi.
- Chú thích nói điều kiện, thứ tự, quyền sở hữu trạng thái và tác động lưu dữ liệu; không chỉ lặp tên hàm.
- Không thêm package.json, Prettier, Node tests, CDN hoặc frontend build vào ứng dụng/CI. Công cụ format tạm nằm trong build bị Git bỏ qua, không cần cho chạy/deploy.
- Java tiếp tục dùng Spotless. Format UI bằng công cụ IDE phù hợp sau khi sửa, rồi kiểm syntax và xem lại giao diện; tránh format tay làm thay đổi biểu thức/escape trong template.

## Kiểm chứng và giới hạn

- Build/Spotless và 81 test Java đạt; test shell có kiểm asset theme.js công khai và shell vẫn không chứa token/CSRF. JS syntax đã kiểm bằng node --check như công cụ tạm, không thêm bộ test Node.
- Browser preview H2 test riêng trên loopback: kiểm bố cục, theme, reload, menu, các trang công khai và kích thước điện thoại. Kết quả chi tiết/ảnh ở STATUS.
- Theo lựa chọn người dùng, không tạo tài khoản/phòng/trận/save và không chơi bằng browser/API preview. Chưa kiểm đa ngón trên thiết bị thật, Safari/Android thật, offline gameplay/cloud hoặc HTTPS/WebSocket Render. Test Java không chứng minh parity engine JS/Java.

Tham khảo: [Pointer Events](https://developer.mozilla.org/en-US/docs/Web/API/Pointer_events), [WebKit safe area](https://webkit.org/blog/7929/designing-websites-for-iphone-x/).
