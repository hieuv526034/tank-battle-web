# Nghiệm thu backend trước UI

Yêu cầu: hoàn thiện nghiệp vụ BE, kiểm thử và tài liệu; UI chỉ thực hiện khi người dùng yêu cầu sau.

| Giai đoạn    | Kết quả đã triển khai                                                                                       |
| ------------ | ----------------------------------------------------------------------------------------------------------- |
| Quy hoạch    | auth/common/business thật, business chia tầng chung; model/DTO riêng, SQL ở repo; xóa thư mục rỗng          |
| API          | Hồ sơ, phòng/current/join/ready/leave/start; state/results/history; ranking Arena/Campaign; catalog và save |
| WebSocket    | Ticket một lần, Origin/member, sequence/rate/size, sync, revoke đóng socket sau commit                      |
| Engine       | Arena/Campaign, ba độ khó/cửa, wave, địch cấu hình, item/buff và kill score                                 |
| Persistence  | Schema 2/nâng schema 1 Arena, checkpoint/recovery/pause/resume, forfeit, batch/idempotent                   |
| Ranking/save | Hai bảng hạng riêng; best score từng cửa; ba slot save, revision kể cả sau xóa/tạo lại                      |
| Test         | H2, PostgreSQL 18.6 cục bộ, TCP socket, transaction/concurrency, auth/Google identity, H2 file restart      |
| Bàn giao     | Swagger, CI source, scripts test/DB check, STATUS/HANDOFF và tài liệu 19–20                                 |

Bằng chứng số test/build đọc [STATUS](STATUS.md). Luồng và tiêu chí đọc [tài liệu 20](20-luong-backend-va-test.md), endpoint đọc [API](11-swagger-va-api.md).

UI/Canvas/engine JS/IndexedDB/Service Worker sẽ thực hiện ở giai đoạn sau. Backend đồng bộ save không tự tạo khả năng offline trong browser.

Google thật cần OAuth Client; deploy public cần HTTPS/RSA keys và backup. Source đã có Git repository và cấu hình GitHub CI/Render; chưa xác nhận các job Docker/GitHub hoặc luồng HTTPS/WebSocket production trong phiên hỗ trợ này.

Neon là production, chỉ được kiểm tra đọc. Migration và test dùng DB riêng. Không dùng test pass để cam kết tuyệt đối không còn lỗi hoặc thay bằng chứng tải/HA.

## Đánh giá sẵn sàng tích hợp UI — 20/10/2026

Backend cốt lõi đủ để bắt đầu UI. Đã đối chiếu yêu cầu P0 với controller/service/repository/runtime và các test nghiệp vụ; chưa nghiệm thu toàn bộ sản phẩm khi chưa có client trình duyệt. Lượt rà soát này: build/Spotless và 79 test Java đạt; 35 test PostgreSQL 18.6 đạt; JAR prod trên DB mới health UP, admin login/API quản trị/API phòng và migration/schema đạt. Không truy cập Neon hoặc chạy UI/Render/Docker thật.

| Luồng | Cơ sở hiện có cho UI |
| --- | --- |
| Tài khoản và hồ sơ | Register/login/logout/refresh/revoke, đổi tên, JWT/CSRF, admin bootstrap và phân quyền |
| Phòng chờ | Danh sách/current/create/join/ready/start/leave, chuyển chủ, giới hạn ghế, presence |
| Online | WebSocket ticket/Origin, input/sync, server quyết định state, Arena/Campaign, độ khó/cửa/địch/item |
| Mất mạng và chơi tiếp | Checkpoint, pause/reconnect, RECOVERING, hạn xác nhận, bỏ cuộc và kết toán idempotent |
| Kết quả và xếp hạng | Lịch sử riêng tư, kết quả từng người, Arena/Campaign riêng, hạng cá nhân và tiến độ |
| Lưu solo | API đồng bộ ba slot có revision/ownership; JSON catalog công khai cho browser tải/cache |

Lần rà soát này phát hiện hai API danh sách bảng hạng đang bắt JWT trái với quyền khách trong P0. Đã mở đúng hai GET /api/rankings và /api/rankings/campaign; route cá nhân vẫn cần JWT. Swagger bỏ yêu cầu Bearer trên hai operation công khai. Test mới kiểm khách đọc được danh sách, bị chặn ở route riêng/phòng và spec khớp quyền.

Khi chuyển sang UI cần thực hiện:

1. Layout/trang chủ/hướng dẫn → đăng ký/đăng nhập/hồ sơ/bảng hạng.
2. API client quản lý CSRF, refresh đồng thời, hết phiên và lỗi field/vi/en.
3. Danh sách phòng/lobby → WebSocket → Canvas/HUD/kết quả → lịch sử và reconnect/recovery.
4. Engine JavaScript cho solo/bot, IndexedDB và Service Worker, tải đủ tài nguyên và chơi/lưu/tiếp tục khi tắt mạng; đồng bộ cloud khi có mạng. Backend Java/API save không tự thực hiện các phần browser này.
5. Kiểm tích hợp nhiều tab và trình duyệt thật: hết JWT khi chơi, mất mạng/reload, trở lại trận sau restart, revision conflict, kết quả và bảng hạng.

Các giới hạn cần giữ: Google callback thật chưa thử; admin hiện có bootstrap/role/API kiểm quyền, chưa có CRUD quản trị catalog; RAM/tải/reverse proxy/backup-restore/kill process chưa nghiệm thu. Vận hành một runtime instance, phát hành khi không có trận đang chơi. Những giới hạn này cần xử lý trước nghiệm thu/phát hành, không ngăn bắt đầu UI cốt lõi.
