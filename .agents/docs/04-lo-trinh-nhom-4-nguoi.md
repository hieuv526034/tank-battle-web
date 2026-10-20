# 04. Lộ trình cho nhóm 4 người

Phạm vi bản nộp nằm trong [kế hoạch tổng thể](00-ke-hoach-tong-the.md); dùng [backlog có mã và tiêu chí nghiệm thu](07-backlog-va-nghiem-thu.md) để phân công, cập nhật tiến độ và review.

## 1. Phân công đề xuất

| Thành viên               | Phụ trách chính                                                       | Sản phẩm bàn giao                                                             | Phối hợp                                      |
| ------------------------ | --------------------------------------------------------------------- | ----------------------------------------------------------------------------- | --------------------------------------------- |
| A — Backend nghiệp vụ    | Tài khoản/hồ sơ, phòng, JPA, lịch sử/xếp hạng, transaction checkpoint | API/trang nghiệp vụ, schema, ranking và dữ liệu phục hồi nhất quán            | B chốt vòng đời/checkpoint; C chốt HTML/DTO   |
| B — Game online          | Engine Java, WebSocket, tick, reconnect, phục hồi sau restart         | Trận online đồng bộ, checkpoint đủ state, đúng một runtime khi resume         | A lưu bền vững; C fixture; D test crash       |
| C — Game client          | Canvas, bàn phím, engine offline/bot, IndexedDB save/resume           | Bàn chơi, adapter, menu tiếp tục và lưu solo theo phiên bản                   | B cùng giữ fixture; D cache/PWA               |
| D — Tích hợp và vận hành | PWA, Docker, CI/CD, kiểm thử crash/recovery và ranking                | Offline sau reload, HTTPS, backup, bằng chứng khôi phục không cộng trùng điểm | C chốt tài nguyên; A/B log/health/persistence |

Mỗi người viết tài liệu và test cho phần mình. D tổng hợp báo cáo/triển khai, không nhận toàn bộ việc viết tài liệu thay các thành viên khác. Mỗi phần quan trọng có ít nhất một người khác đọc và chạy thử.

## 2. Những việc phải thống nhất trước khi chia nhánh

1. Luật chơi, hình dạng state, cấu hình map và quy ước tọa độ.
2. Request/response HTTP, input/snapshot WebSocket và mã lỗi.
3. Cấu trúc thư mục, tên package, phiên bản Java/Spring/PostgreSQL.
4. Quy tắc Git: mỗi việc một nhánh ngắn, PR nhỏ vào main, CI đạt trước khi merge.
5. Cách chạy và dữ liệu thử nghiệm không chứa thông tin cá nhân thật.

Tạo issue theo tính năng có tiêu chí xong cụ thể. Ví dụ: “Hai người đồng thời tranh ghế cuối, chỉ một người vào được” tốt hơn “Làm room backend”.

## 3. Lộ trình tham chiếu 8 tuần

Ước lượng dành cho nhóm đã biết Java cơ bản và HTML/CSS; chưa tính thời gian học lập trình từ đầu. Đã tăng từ 6 lên 8 tuần sau yêu cầu bắt buộc về khôi phục cả hai chế độ và xếp hạng. Điều chỉnh theo hạn nộp và thời lượng làm thực tế.

| Giai đoạn | Công việc chính                                                                                      | Điều kiện xong                                                              |
| --------- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Tuần 1    | Chốt yêu cầu; skeleton Spring; kết nối PostgreSQL; Flyway; CI; chọn máy chủ và deploy trang đơn giản | Clone mới chạy được; pipeline kiểm tra build; mở trang thử nghiệm qua HTTPS |
| Tuần 2    | Song song làm tài khoản/phòng và game solo Canvas: di chuyển, đạn, tường, bot                        | Tạo/vào phòng được; solo chạy được khi có mạng; fixture luật được chốt      |
| Tuần 3    | Lobby/ready, engine Java, WebSocket và solo/bot                                                      | Hai engine đúng fixture; quyền phòng/message được kiểm tra                  |
| Tuần 4    | Đồng bộ trận online, đủ số ghế, kết quả/chơi lại                                                     | Hai tài khoản thấy cùng trận; kết quả lưu một lần; phòng đầy đúng luật      |
| Tuần 5    | Reconnect, lịch sử, cache offline, IndexedDB save và checkpoint PostgreSQL                           | Đóng tab mở lại tiếp tục solo; checkpoint được ghi không đè ngược           |
| Tuần 6    | Khôi phục sau restart, schema bản lưu, lỗi DB, đồng thời resume                                      | Kill process rồi tiếp tục cùng matchId; không tạo runtime/kết quả trùng     |
| Tuần 7    | Hồ sơ/xếp hạng, CD, backup/restore/rollback, đo tải                                                  | Điểm đúng sau recovery; deploy có health/smoke; restore đã thử              |
| Tuần 8    | Rà soát code/comment; báo cáo, slide, diễn tập                                                       | Người ngoài chạy theo README; mọi P0 có bằng chứng                          |

Triển khai một trang từ tuần đầu giúp phát hiện sớm vấn đề JDK, container, DNS, HTTPS và WebSocket trên máy chủ. Không đợi game hoàn thiện mới thử deploy.

## 4. Thứ tự làm chi tiết

### Mốc A — Nền móng chạy được

- Tạo Git repository, Gradle Wrapper, Spring Boot project.
- Chọn dependency theo bộ phiên bản đã thống nhất: MVC, Thymeleaf, Security, Validation, JPA, PostgreSQL, Flyway, WebSocket, Actuator và test.
- Tạo cấu hình dev/test/prod và `.env.example` không có bí mật.
- PostgreSQL local qua Compose; migration đầu tiên; một trang Thymeleaf.
- CI chạy kiểm tra build/test, sau đó đóng gói Docker và deploy trang thử nghiệm.
- Viết hướng dẫn từ máy sạch, chụp bằng chứng trang đang chạy.

### Mốc B — Game nhỏ có thể chơi

- Renderer chỉ vẽ hình đơn giản; logic dùng tọa độ game độc lập với kích thước CSS.
- Một người chơi, tường, một bot, máu và điều kiện kết thúc.
- Tách engine khỏi DOM; thêm fixture cho va chạm và cooldown.
- Cùng lúc hoàn thành đăng nhập, tạo/vào phòng và ready.

### Mốc C — Online thực sự

- Cho hai client kết nối, đồng bộ input/snapshot.
- Máy chủ quyết định vị trí và sát thương; client chỉ gửi ý định.
- Hoàn thiện ràng buộc phòng, chống start lặp, cô lập từng phòng.
- Mở rộng tới số ghế đã chọn, xử lý mất mạng và lưu kết quả.

### Mốc D — Offline thực sự

- Dùng entry HTML tĩnh, bot và cấu hình đã có; loại bỏ phụ thuộc API khỏi chế độ offline.
- Tạo Service Worker, manifest, danh sách precache và kiểm tra sẵn sàng.
- Kiểm tra sau khi đóng tab, tắt backend và ngắt mạng, không chỉ để tab đang mở.
- Lưu state vào IndexedDB, có menu Tiếp tục; khôi phục bot, HP, cooldown, đồng hồ và xử lý lỗi lưu.
- Thử cập nhật phiên bản game và giữ bản cũ khi download bản mới thất bại.

### Mốc E — Khôi phục online và xếp hạng

- Checkpoint đầu/định kỳ/cuối, giữ map/rules của trận; test ghi trễ không đè bản mới.
- Startup nạp trận dở vào RECOVERING, xác nhận đúng người và tiếp tục cùng matchId.
- Kill process, restart lần nữa khi đang recovery, thử hết hạn/bỏ cuộc và bản lưu không tương thích.
- Bảng điểm từ FINISHED; test đồng hạng, phân trang, vị trí của mình và không cộng trùng sau crash.

### Mốc F — Bản nộp và vận hành

- CD sử dụng đúng image đã kiểm tra; health check và smoke test sau deploy.
- Backup PostgreSQL, phục hồi sang database thử, thử rollback image tương thích schema.
- Hoàn thành tài liệu và demo, đóng các lỗi chặn người chơi.

## 5. Hồ sơ cần nộp

1. Phân tích yêu cầu, đối tượng người dùng và phạm vi.
2. Use case, kiến trúc, ERD và sequence các luồng chính.
3. Mô tả engine, input/snapshot/checkpoint, phục hồi online/offline và công thức xếp hạng.
4. Hướng dẫn cài đặt local và biến môi trường.
5. Hướng dẫn deploy, secrets, backup/restore, rollback, xử lý sự cố.
6. Kế hoạch kiểm thử, kết quả thực tế và ảnh/video bằng chứng.
7. Phân công, các mốc hoàn thành và những giới hạn còn lại.

Những file kế hoạch hiện tại là khung ban đầu. Khi code thay đổi, sửa tài liệu theo hành vi thực tế và ghi liên kết tới lớp/hàm tương ứng.
