# 28. Rà soát chất lượng ngày 21/10/2026

Source được kiểm: commit d725c22, lượt hoàn thiện UI/Canvas và tài liệu tiếng Việt. Đây là đánh giá sau khi UI đã có; không áp dụng lại trạng thái backend-only ở các tài liệu lịch sử.

Ba lỗi bên dưới đã được xử lý ở lượt tiếp theo. Xem [29](29-sua-loi-va-bo-cuc-ban-choi.md) và STATUS
cho source/kiểm chứng hiện tại; giữ tài liệu này làm bằng chứng lỗi trước khi sửa.

## Kết luận

Backend/build và các luồng chính đã có bằng chứng tốt, website Render đang phản hồi. Chưa nghiệm thu hoàn tất đồ án vì còn ba lỗi JS đã tái hiện và các tình huống nhiều người/thiết bị chưa được kiểm chứng. Lượt này chỉ rà soát, chưa sửa logic ứng dụng, env/key/migration hoặc commit/push.

## Các lỗi được xác nhận

### P2 — tải cloud có thể ghi sang vùng local của tài khoản vừa đổi

Vị trí: [app.js](../../src/main/resources/static/js/app.js), hàm downloadSave khoảng dòng 1866–1880; caller download-save giữ khóa theo scope trước khi gọi ở khoảng dòng 2136.

Hàm đợi GET /api/offline-saves/{slot} xong mới gọi scope() để chọn owner. Nếu request được gửi bằng JWT của A, sau đó một tab khác đổi phiên sang B trước khi response về, snapshot A được ghi vào slot local B. Caller vẫn đang giữ khóa slot A, nên cả dữ liệu lẫn khóa không còn cùng scope; có thể ghi đè tiến trình B. Sự kiện đổi danh tính chỉ điều hướng/dừng game, không hủy promise đồng bộ đang chờ.

Tái hiện trong harness dùng nguyên hàm downloadSave: API trả promise chờ với requestOwner=user-1 → đổi scope thành user-2 → resolve snapshot user-1 → storage.saveGame nhận owner=user-2. Harness chỉ dùng đối tượng giả trong RAM, không đổi phiên người dùng thật hoặc gọi API production.

Hướng sửa: chụp owner/danh tính/thế hệ phiên trước request, kiểm tra vẫn là cùng người sau mỗi await và trước khi ghi; dùng cùng owner cho API sync, khóa và storage. Kiểm tra tương tự upload/create/delete-cloud thay vì chỉ sửa một dòng download.

### P2 — Arena solo chết đồng thời bị ghi LOSS thay DRAW

Vị trí: [storage.js](../../src/main/resources/static/js/storage.js) finishGame, khoảng dòng 121–135; [app.js](../../src/main/resources/static/js/app.js) finish/draw khoảng dòng 1547. Đối chiếu MatchService.outcome ở Java.

Engine JS đã ghi eliminatedAtTick và endReason=MUTUAL_ELIMINATION đúng. Nhưng cả nhãn kết quả và IndexedDB chỉ coi TIME_LIMIT/còn sống là DRAW. Hai xe chết cùng tick cuối vì vậy bị hiện/ghi thua, lệch hợp đồng kết quả Arena của Java; các xe chết từ trước vẫn phải LOSS.

Tái hiện: createGame Arena/1 bot, HP hai xe bằng 1, hai đạn bắn trúng trong cùng tick. Kết quả thực: endReason=MUTUAL_ELIMINATION, hp=0, eliminatedAtTick=tick=1; gọi finishGame thực với IndexedDB mô phỏng nhận outcome=LOSS. Theo quy tắc Java cần DRAW cho người bị loại đúng tick cuối.

Hướng sửa: dùng một hàm xác định outcome solo cho cả UI và storage, kiểm tick bị loại và forfeited; giữ Campaign theo điều kiện clear/còn sống. Thêm các ca cùng chết cuối, chết trước tick cuối, timeout và bỏ cuộc.

### P2 — Service Worker không fallback vỏ offline với HTTP 5xx

Vị trí: [sw.js](../../src/main/resources/static/sw.js) navigation handler, dòng 39.

Handler chỉ dùng caches.match('/offline') trong catch của fetch. HTTP 503/502 là response thành công về mặt Promise, nên không đi vào catch; navigation trả trang lỗi máy chủ dù cache solo đã tải đủ. Người dùng vẫn có mạng nhưng server/proxy tạm lỗi sẽ không mở được vỏ solo từ navigation đó.

Tái hiện: thực thi nguyên sw.js trong context Service Worker mô phỏng, cache có vỏ offline 200 và fetch trả 503; response navigation vẫn là 503. Đây là lỗi được chứng minh bằng fixture, không khẳng định Render đang trả 503 — health Render hiện UP.

Hướng sửa: với navigation công khai, xét response 5xx rồi dùng vỏ cached nếu có; nếu cache chưa đủ thì trả response/lỗi phù hợp. Không áp dụng fallback/cache này cho API/auth hoặc dùng nó để bỏ quyền dữ liệu riêng.

## Kiểm thử đã chạy

- Gradle offline/no-build-cache build + Spotless: **81 test Java, 0 failure/error/skipped**. Báo cáo đầy đủ ở build/test-results/test và build/reports/tests/test, không phải lượt lọc ít test.
- PostgreSQL 18.6 loopback riêng: **37 test, 0 failure/error/skipped**. Prod-profile JAR mới chạy health UP, auth 401/en, bootstrap admin login/ADMIN API, rooms API và Flyway V1–V6/schema đạt. App/cluster/key test được script dừng/dọn.
- node --check cho sw.js và parse module cho tất cả static/js: đạt. Không thêm Node dependency, package.json hoặc test runner frontend vào source/CI.
- Harness tạm build/verification/review-cases.mjs gọi engine/storage và hàm downloadSave thực, cùng mô phỏng Service Worker; xác nhận ba tình huống bên trên. Harness dùng RAM/fixture, không Neon, không tài khoản/bản lưu production.
- Public Render qua trình duyệt: trang chủ mở được, nhãn tài nguyên offline sẵn sàng; /actuator/health/readiness trả {"status":"UP"}; bảng xếp hạng công khai tải được và hiển thị trạng thái chưa có thành tích. Không đăng nhập, tạo phòng/trận/tài khoản/save trên production. Công cụ web/native HTTP trước đó không truy cập được/timeout, nên dùng kết quả trình duyệt thay vì kết luận server hỏng.

## Còn cần nghiệm thu

- Nhiều trình duyệt/người chơi online cùng trận; mất mạng rồi reconnect và kill/restart server giữa trận với checkpoint thật.
- Cloud conflict nhiều thiết bị và đổi danh tính giữa các request đang chạy; refresh khi nhiều tab ẩn/hiện.
- Parity engine Java/JS qua fixture chung; hiện có lỗi outcome cụ thể bên trên dù các engine đã có nhiều luật tương tự.
- Touch nhiều ngón trên Android/iOS thật, FPS/RAM; responsive preview không chứng minh các mục này.
- Google OIDC thật, workflow/Docker smoke trên GitHub cho commit triển khai, backup/restore production. Máy hiện tại không có Docker CLI.
- Admin hiện có bootstrap/role/API kiểm quyền; chưa có CRUD API hoặc màn hình quản trị catalog địch/item/cửa.

Không đổi lại chính sách commit env/key mà chủ đồ án đã chọn. Mọi bằng chứng ở đây tránh ghi nội dung key, mật khẩu, token hoặc cookie.
