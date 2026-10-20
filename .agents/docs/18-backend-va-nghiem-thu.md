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

Google thật cần OAuth Client; deploy public cần máy chủ/domain/HTTPS/RSA keys và backup. CI đã viết nhưng chưa chạy trên GitHub khi chưa có repository/remote.

Neon là production, chỉ được kiểm tra đọc. Migration và test dùng DB riêng. Không dùng test pass để cam kết tuyệt đối không còn lỗi hoặc thay bằng chứng tải/HA.
