# 01. Phạm vi và luật chơi

## 1. Điều đã được xác nhận

- Đồ án do nhóm **4 người làm**.
- Game web 2D, đồ họa đơn giản, có tạo phòng để chơi online.
- Bản online có đăng ký, đăng nhập và các luồng phòng chơi; yêu cầu được phân rã trong [kế hoạch tổng thể](00-ke-hoach-tong-the.md).
- Có bảng xếp hạng; cả online và offline phải tiếp tục được trận dở sau khi đóng trình duyệt hoặc server khởi động lại theo quy tắc khôi phục.
- Có chế độ offline trong trình duyệt, không yêu cầu người chơi chạy Java.
- Công nghệ học tập: Java Spring Boot, Thymeleaf, PostgreSQL.
- Có triển khai thực tế và CI/CD; có comment và tài liệu ghi lại luồng.
- Hiện chưa có máy chủ.

Đề tài xe tăng lấy theo tên thư mục `tank-battle-web`; đây là phương án đề xuất. Nội dung bên dưới dùng phương án này để có kế hoạch cụ thể.

## 2. Phạm vi MVP

MVP là bản nhỏ nhất có thể chơi và trình bày đầy đủ từ giao diện đến máy chủ.

| Chế độ  | Người dùng làm được                                                                                               | Nơi xử lý luật              | Nơi lưu dữ liệu                                |
| ------- | ----------------------------------------------------------------------------------------------------------------- | --------------------------- | ---------------------------------------------- |
| Online  | Đăng ký/đăng nhập/đăng xuất, hồ sơ, danh sách/tạo/vào phòng, sẵn sàng, chơi, reconnect, kết quả/lịch sử, chơi lại | Java trên máy chủ           | PostgreSQL cho tài khoản, phòng và kết quả     |
| Offline | Solo với 1–3 bot, lưu/tiếp tục trận dở, chơi lại, xem kết quả trên thiết bị                                       | JavaScript trong trang game | IndexedDB cho một slot dở và tối đa 20 kết quả |

Số người online đề xuất là 2–4 người/phòng, độc lập với số thành viên nhóm. Bản đầu không cần bot trong phòng online.

Online và offline dùng chung giao diện bàn chơi, tài nguyên, bản đồ và cấu hình luật. Offline không cần đăng nhập và không gửi điểm lên bảng xếp hạng online.

## 3. Luật chơi đề xuất

| Thành phần | Luật ban đầu                                                                            |
| ---------- | --------------------------------------------------------------------------------------- |
| Góc nhìn   | Nhìn từ trên xuống, bản đồ cố định 2D                                                   |
| Bản đồ     | Lưới 24 × 18 ô, mỗi ô 32 đơn vị; khung logic 768 × 576                                  |
| Điều khiển | WASD hoặc phím mũi tên di chuyển, Space bắn                                             |
| Di chuyển  | Bốn hướng, giữ một hướng tại một thời điểm; hướng di chuyển là hướng nòng súng          |
| Máu        | Mỗi xe có 3 HP; mỗi viên đạn trúng gây 1 sát thương                                     |
| Đạn        | Đi thẳng, biến mất khi chạm tường, biên hoặc xe khác; có thời gian chờ giữa hai lần bắn |
| Tường      | Cố định, không phá hủy trong bản đầu                                                    |
| Va chạm    | Xe không xuyên tường/xe khác; xử lý theo thứ tự ID ổn định mỗi tick                     |
| Kết thúc   | Còn một xe sống thì xe đó thắng; tất cả chết cùng tick thì hòa                          |
| Giới hạn   | Trận tối đa 3 phút; hết giờ còn nhiều xe thì hòa                                        |
| Bot        | Đổi hướng khi gặp tường, bắn khi có đối thủ cùng hàng/cột và đường đạn trống            |

Tốc độ xe, tốc độ đạn và cooldown nằm trong một file cấu hình chung; nhóm tinh chỉnh bằng chơi thử. Xử lý đường đi của đạn giữa hai tick để tránh đạn đi xuyên mục tiêu. Tính các va chạm của cùng tick trước khi kết luận thắng/thua.

## 4. Vòng đời phòng online

1. Người đã đăng nhập tạo phòng, trở thành chủ phòng.
2. Người khác nhập mã phòng và chiếm một ghế trống.
3. Mỗi người bật trạng thái sẵn sàng.
4. Chủ phòng bắt đầu khi đủ tối thiểu 2 người, tất cả sẵn sàng và kết nối game hợp lệ.
5. Trong trận không nhận người chơi mới.
6. Kết thúc, lưu kết quả rồi cho thành viên về phòng chờ để chơi lại.

Một tài khoản chỉ ở một phòng hoạt động tại một thời điểm. Chủ phòng rời thì chuyển quyền cho thành viên vào sớm nhất; nếu rời giữa trận, quyền mới phục vụ phòng chờ kế tiếp và không thay đổi luật trận. Phòng trống được đóng. Thành viên mất kết nối ở phòng chờ quá 60 giây được tự giải phóng ghế. Roster thay đổi thì reset ready. Trong trận, chủ phòng không có quyền quyết định thắng/thua.

Mất kết nối: xe dừng nhận điều khiển, người chơi có tối đa 30 giây để kết nối lại với cùng tài khoản. Xe vẫn có thể bị bắn. Quá thời gian thì bị loại. Trận vẫn tuân theo luật kết thúc; kết nối lại không phục hồi xe đã bị loại. Nếu toàn bộ người chơi mất kết nối và hết thời gian chờ, ưu tiên đánh dấu ABORTED, không cộng thành tích. Chủ động rời trận là bỏ cuộc ngay; nếu trận FINISHED thì người đã bị loại/bỏ cuộc được tính thua.

Phòng lưu WAITING/RUNNING/RECOVERING/CLOSED. Trong khi kết quả đang được lưu, phòng vẫn RUNNING; sau commit chuyển về WAITING/reset ready, hoặc CLOSED nếu đã trống. Trận lưu RUNNING/RECOVERING/FINISHED/ABORTED. Sau restart, nạp checkpoint và chờ người chơi cần thiết xác nhận; [tài liệu 08](08-luu-khoi-phuc-va-xep-hang.md) quy định thời hạn, lỗi và cách tiếp tục.

## 5. Offline thực sự hoạt động thế nào?

- Lúc có mạng, người dùng mở trang offline và tải trước toàn bộ HTML tĩnh, CSS, JavaScript, bản đồ và tài nguyên cần thiết.
- Service Worker phục vụ lại những tài nguyên này khi mất mạng. Luật game chạy trong JavaScript của trang, không chạy trong Service Worker.
- Chỉ hiển thị “Sẵn sàng chơi offline” sau khi tải đủ và kiểm tra worker đã kiểm soát trang.
- Lần truy cập đầu tiên khi chưa có tài nguyên và không có mạng sẽ không chơi được.
- Xóa dữ liệu website hoặc trình duyệt thu hồi bộ nhớ có thể làm mất bản tải/điểm cục bộ.
- Offline autosave vào IndexedDB, mở lại thấy Tiếp tục trận và có thể dùng bản lưu đã commit gần nhất. Không dựa hoàn toàn vào sự kiện đóng tab để lưu.
- Rớt mạng trong trận online hiển thị kết nối lại. Nếu chọn chơi offline thì tạo trận solo mới, không tiếp tục phòng online.

Website triển khai cần HTTPS để dùng Service Worker; localhost được hỗ trợ cho phát triển. Cơ chế tải trước và điều kiện chạy được mô tả trong [MDN: Offline and background operation](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Offline_and_background_operation).

## 6. Ưu tiên mở rộng sau MVP

Âm thanh đơn giản, nhiều bản đồ, hỗ trợ cảm ứng, bảng xếp hạng theo mùa, bot online hoặc mời bạn bằng liên kết là các hạng mục bổ sung. Chat, ghép trận tự động và đồng bộ điểm offline nằm ngoài phạm vi bản đầu. Bảng xếp hạng toàn thời gian và khôi phục sau server restart đã thuộc P0 theo yêu cầu bổ sung.

## 7. Điều kiện nghiệm thu

- Hai trình duyệt độc lập đăng nhập, vào cùng phòng và thấy cùng diễn biến trận.
- Đăng ký, đăng nhập/đăng xuất, hồ sơ, kết quả/lịch sử và chơi lại đạt các tiêu chí trong [backlog](07-backlog-va-nghiem-thu.md).
- Thử phòng đầy, người thứ năm bị từ chối, người ngoài không đọc/điều khiển phòng khác.
- Máy chủ kiểm tra cooldown, tốc độ và sát thương; sửa JavaScript client không tự quyết định kết quả online.
- Tải game, đóng tab, ngắt mạng, mở lại trang offline vẫn chơi với bot được.
- Solo tiếp tục đúng bản lưu; cắt process server rồi khởi động lại có thể xác nhận và tiếp tục cùng trận online từ checkpoint.
- Ranking tính đúng từ FINISHED và không tăng trùng khi khôi phục/lưu kết quả lặp.
- Dừng backend sau khi tải đủ game: offline vẫn chạy; online thông báo lỗi/kết nối lại rõ ràng.
- Tài khoản và kết quả đã lưu còn sau khi tạo lại container ứng dụng.
- Push thay đổi hợp lệ qua pipeline tạo được phiên bản chạy trên HTTPS.
- Có README, sơ đồ luồng, ERD, hướng dẫn vận hành và bằng chứng kiểm thử.
