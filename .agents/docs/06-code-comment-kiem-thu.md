# 06. Code dễ học, comment và kiểm thử

## 1. Nguyên tắc viết code

- Mỗi lớp/module có một nhiệm vụ, tên diễn đạt nghiệp vụ như `RoomService`, `CollisionDetector`, `OfflineGameEngine`.
- Controller nhận dữ liệu, validate và gọi service; service quyết định nghiệp vụ; repository truy cập dữ liệu.
- Engine không chứa SQL, HTTP, Thymeleaf hoặc Canvas. Renderer không quyết định kết quả online.
- DTO dành cho request/response, không gửi thẳng JPA entity ra client.
- Các số như HP, cooldown, tốc độ và thời gian chờ có tên và lấy từ cấu hình chung.
- Ưu tiên cấu trúc rõ ràng, vòng lặp/hàm nhỏ và dependency cần thiết; giải thích annotation Spring ở lần xuất hiện đầu trong module.
- Viết chức năng cùng tài liệu/test liên quan trong một thay đổi để tránh code và hướng dẫn lệch nhau.

## 2. Comment chi tiết nhưng có ích

Comment cần giải thích **mục đích, luật và lý do**, nhất là ở điểm có nhiều luồng hoặc liên quan an toàn dữ liệu.

| Vị trí              | Cần giải thích                                                       |
| ------------------- | -------------------------------------------------------------------- |
| Đầu lớp/module      | Vai trò, ai gọi, dữ liệu vào/ra và liên hệ với lớp khác              |
| Hàm nghiệp vụ chính | Điều kiện trước, kết quả, lỗi có thể xảy ra                          |
| Transaction/lock    | Vì sao phải khóa, race condition nào cần tránh                       |
| Tick/input queue    | Luồng nào sở hữu state, vì sao handler không sửa trực tiếp           |
| Va chạm/cooldown    | Đơn vị, thứ tự xử lý, trường hợp biên                                |
| Online/offline      | Phần dùng chung, phần chạy riêng và ranh giới tin cậy                |
| Save/recovery       | State bắt buộc lưu, phiên bản, thứ tự commit và điều kiện khôi phục  |
| Ranking             | Công thức điểm, điều kiện FINISHED, đồng hạng và cách tránh tính lặp |
| Cấu hình/CI/CD      | Từng nhóm cấu hình làm gì và điền giá trị ở đâu                      |

Ví dụ rút từ luồng thật `RoomService.join`; đọc phần triển khai đầy đủ tại
[RoomService](../../src/main/java/vn/edu/utc/tankbattle/business/service/RoomService.java).
Javadoc giải thích điều kiện, thứ tự khóa và ý nghĩa của việc gửi lặp:

```java
/**
 * Cấp ghế trống trong phòng WAITING; khóa phòng trước user để tránh tranh ghế cuối
 * và một tài khoản tham gia hai phòng cùng lúc.
 *
 * Gửi lại yêu cầu vào đúng phòng đang giữ không tạo thêm membership.
 * Roster thay đổi thì reset ready; caller chỉ phát lobby sau khi transaction thành công.
 */
```

Không cần lặp lại ý nghĩa từng phép gán. Khi sửa luật, sửa comment và tài liệu tương ứng. Tên biến/hàm trong code nên dùng tiếng Anh thống nhất, diễn giải và comment có thể dùng tiếng Việt để nhóm học.

## 3. Tài liệu của mỗi tính năng

Mỗi tính năng có một mục hoặc file gồm:

1. Mục đích và điều kiện sử dụng.
2. Các bước người dùng thao tác và phản hồi nhìn thấy.
3. Luồng `page/JS → controller/handler → service/engine → repository/database`.
4. Input/output, quyền, lỗi và transaction nếu có.
5. Ví dụ chạy thử, kết quả mong đợi và bằng chứng thực tế.
6. Liên kết tới file/lớp chính, giới hạn và những việc còn lại.

Với sự thay đổi kiến trúc, ghi một quyết định ngắn: vấn đề, lựa chọn, lý do và hệ quả. Ví dụ: tại sao phải có JavaScript engine offline và Java engine online.

## 4. Ma trận kiểm thử cần thực hiện

Các mục dưới đây là ma trận thiết kế, gồm cả backend đã kiểm thử và UI/deploy còn lại.
Đối chiếu [STATUS](STATUS.md) để biết từng bằng chứng thực tế; không xem toàn bộ ma trận là đã chạy.

| Nhóm               | Tình huống                                                           | Kết quả mong đợi                                                                     |
| ------------------ | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Luật game          | Xe đi tới tường/biên/xe khác                                         | Không xuyên vật cản, vị trí hợp lệ                                                   |
| Luật game          | Giữ phím bắn                                                         | Số đạn tuân theo cooldown                                                            |
| Luật game          | Đạn vượt qua xe trong khoảng giữa hai tick                           | Phát hiện trúng, không xuyên mục tiêu                                                |
| Luật game          | Nhiều xe chết cùng tick, hết giờ, còn một xe                         | Kết quả đúng luật và ổn định thứ tự                                                  |
| Hai engine         | Cùng fixture state/input/timestep/map                                | Java và JavaScript có kết quả tương đương theo sai số tọa độ đã định                 |
| Tài khoản          | Trùng username, mật khẩu sai, dữ liệu không hợp lệ                   | Từ chối đúng, không lộ dữ liệu nhạy cảm                                              |
| Phiên              | Đăng xuất/hết phiên khi WebSocket đang kết nối                       | Đóng kết nối liên quan, lệnh phiên cũ bị từ chối                                     |
| Hồ sơ              | Sửa tên tài khoản khác, tên chứa markup, thống kê có ABORTED/offline | Chỉ sửa tên của mình, hiển thị text an toàn, thống kê không cộng sai                 |
| Đồng thời          | Hai người tranh ghế cuối                                             | Không vượt sức chứa, không trùng ghế                                                 |
| Phòng              | Một tài khoản vào hai phòng, start gửi hai lần                       | Membership/transaction không tạo dữ liệu sai                                         |
| Phòng chờ          | Chủ rời, roster đổi, người mất mạng quá 60 giây                      | Chuyển chủ đúng, reset ready, dọn ghế, đóng phòng trống                              |
| Quyền              | Người ngoài gửi input hoặc subscribe phòng khác                      | Bị từ chối cả đọc và ghi                                                             |
| Đồng bộ            | Nhiều phòng chạy cùng lúc                                            | Không lẫn player/snapshot/kết quả                                                    |
| Kết nối            | Input trùng/cũ, mất keyup, tab nền                                   | Bỏ input cũ; xe không tự chạy mãi                                                    |
| Reconnect          | Nối lại trong thời hạn; nối lại sau khi bị loại                      | Lấy state thật, không hồi sinh sai luật                                              |
| Kết quả            | Sự kiện kết thúc gửi lặp; DB lỗi tạm thời                            | Không nhân đôi kết quả, phản hồi trạng thái lưu đúng                                 |
| Chơi tiếp/lịch sử  | Kết thúc rồi chơi lại; người dùng đã rời phòng mở trận cũ            | Reset ready, match mới riêng biệt, lịch sử còn và kiểm tra quyền theo người tham gia |
| Offline            | Tải đủ, đóng tab, ngắt mạng, mở lại/reload                           | Chơi bot được, không gọi API bắt buộc                                                |
| Offline            | Dừng backend sau khi tải đủ                                          | Game offline vẫn chơi bình thường                                                    |
| Offline            | Xóa cache hoặc chưa tải đủ                                           | Không báo sẵn sàng sai; hướng dẫn tải lại khi có mạng                                |
| Cập nhật           | Tải bản mới lỗi hoặc tab cũ vẫn mở                                   | Bản hiện tại tiếp tục dùng tài nguyên tương thích                                    |
| Solo dở dang       | Autosave, đóng trình duyệt, mở lại không mạng                        | Tiếp tục đúng checkpoint, timer/HP/bot/cooldown; lỗi lưu được báo                    |
| Deploy/recovery    | Kill app, tạo lại container với cùng DB                              | Tài khoản/kết quả còn; trận vào RECOVERING và tiếp tục cùng matchId                  |
| Đồng thời recovery | Hai resume cùng lúc; ghi checkpoint trễ sau FINISHED                 | Một runtime, không đè state cuối, không cộng kết quả lặp                             |
| Ranking            | WIN/DRAW/LOSS, đồng hạng, phân trang, crash quanh commit             | Điểm đúng từ FINISHED, vị trí cá nhân đúng, không cộng trùng/offline/ABORTED         |
| Khôi phục          | Restore database thử và rollback image                               | Dữ liệu thử đọc được, bản cũ chạy khi schema tương thích                             |

Unit test dành cho luật game; integration test dùng PostgreSQL thật để kiểm tra constraint/transaction. Test trình duyệt dành cho Canvas/input, WebSocket, session và offline. Không dùng riêng bốn tab cùng một session để kết luận bốn người chơi độc lập; dùng các browser profile/context hoặc thiết bị riêng.

Đo tải theo mốc đề xuất: một phòng đủ người, rồi nhiều phòng như 5 phòng × 4 người; theo dõi thời gian tick, trễ snapshot, CPU, RAM và lỗi. Đây là kịch bản đo ban đầu, không phải cam kết hệ thống đã chịu được mức tải đó.

## 5. Kiến thức nên học theo thứ tự

1. Java: class, collection, enum, exception, interface, xử lý thời gian và kiến thức thread cơ bản.
2. Web: HTTP, form, session/cookie, JSON, CSRF và sự khác nhau giữa client/server.
3. Spring: dependency injection, MVC, validation, service/repository, transaction và Security.
4. PostgreSQL: khóa chính/ngoại, unique index, transaction, migration, dump/restore.
5. JavaScript: module, event bàn phím, Canvas, fixed timestep và requestAnimationFrame.
6. WebSocket trực tiếp đang dùng: handshake, message input/sync, xác thực, reconnect và snapshot.
7. Offline: shell tĩnh, Service Worker cache, vòng đời cập nhật và lưu cục bộ.
8. Persistence game: IndexedDB, checkpoint đủ state, phiên bản bản lưu, transaction và khôi phục idempotent.
9. Ranking: aggregate dữ liệu, điều kiện tính điểm, window function và phân trang.
10. Triển khai: Docker, Compose, GitHub Actions, DNS, HTTPS, log và health check.

Học bằng cách lần theo một luồng hoàn chỉnh trước, ví dụ tạo phòng. Sau đó áp dụng cùng cách đọc sang lượt cập nhật game và lưu kết quả.

## 6. Kịch bản demo đồ án

1. Giới thiệu nhóm 4 người, phạm vi game và kiến trúc hai chế độ.
2. Đăng nhập hai tài khoản độc lập; tạo phòng, nhập mã, ready và start.
3. Chơi một trận ngắn; chỉ ra máy chủ kiểm tra sát thương/cooldown; xem kết quả đã lưu.
4. Thử vào phòng đầy hoặc truy cập phòng không có quyền; hiển thị phản hồi.
5. Tải offline, chơi solo một đoạn, đóng tab/trình duyệt, ngắt mạng, mở lại và chọn Tiếp tục.
6. Kill process server trong một trận online; khởi động lại, các người còn sống xác nhận và tiếp tục cùng trận từ checkpoint.
7. Kết thúc trận rồi xem bảng xếp hạng; chỉ ra recovery không tạo thêm kết quả/điểm.
8. Giới thiệu một thay đổi nhỏ, run CI/CD, image và release HTTPS.
9. Trình bày bằng chứng backup/restore, rollback và giới hạn checkpoint/cửa sổ phục hồi.

Mỗi người giải thích được phần mình và ít nhất một luồng xuyên suốt. Chỉ ghi các kết quả thực sự đã chạy vào báo cáo nghiệm thu.
