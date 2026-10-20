# Transaction và xử lý tranh chấp

Transaction đặt tại service theo một thao tác nghiệp vụ. Unique/FK là lớp bảo vệ cuối; synchronized chỉ bảo vệ runtime trong một JVM, không thay khóa DB. Các test auth/room/kết toán được chạy trên H2 và PostgreSQL riêng, kết quả cuối xem [STATUS](STATUS.md).

## Thứ tự khóa

| Thao tác                    | Cơ chế                                                                    |
| --------------------------- | ------------------------------------------------------------------------- |
| Tạo phòng                   | Guard CREATE_ROOM → user → phòng mới; count+insert cùng transaction       |
| Join/ready/leave            | Room trước user; PK user_id và unique(room_id,seat)                       |
| Kết toán/abort/pause/resume | Room → match; terminal guard FINISHED/ABORTED                             |
| Startup nhiều trận          | Khóa room ID tăng dần theo một query rồi batch đổi trạng thái             |
| Checkpoint                  | Khóa match, chỉ RUNNING; không chờ ngược room                             |
| Offline save                | Khóa user bảo vệ insert đầu, cập nhật có expected revision                |
| Refresh                     | Khóa auth_session, đọc lại token rồi consume/insert; replay revoke commit |

Không giữ transaction quanh cả trận. Tick chỉ RAM; lưu ở executor riêng, queue giới hạn. SQL/batch ở repository, không gọi repository trong vòng lặp player/entity. Checkpoint ghi nguyên snapshot; kết toán ghi tổng cuối theo batch, không increment điểm khi retry.

Bootstrap admin: ApplicationRunner qua proxy @Transactional khóa hàng BOOTSTRAP_ADMIN do V6 tạo, kiểm username rồi insert một lần. Không gọi BusinessException cho lỗi cấu hình startup, không tự nâng USER hoặc ghi đè hash admin đã có. AuthSystemTest chạy hai bootstrap đồng thời trên H2/PostgreSQL; xem [luồng admin](24-bootstrap-admin-va-phan-quyen.md).

## Refresh và sự kiện thu hồi

Riêng refresh có noRollbackFor=AuthFailure vì replay phải commit revoke trước khi trả AUTH_007. Lỗi ngoài dự kiến vẫn rollback. Hai request cùng refresh: một rotate được, request dùng lại thu hồi family, access mới của family cũng bị từ chối.

TokenService phát ApiSessionsRevokedEvent; listener TransactionalEventListener chỉ đóng socket sau commit. Rollback không được làm đóng socket hợp lệ. Logout web session là event riêng, không ép auth import business.

## Checkpoint và kết quả

Tick đơn điệu, checkpoint cũ không đè tick mới hoặc FINISHED. Deadline phục hồi giữ qua restart, không gia hạn. Pause ghi snapshot và RECOVERING cùng transaction; resume chỉ khi deadline còn hạn. Explicit leave ghi forfeited_at trước xóa membership; timeout trong runtime được ghi qua checkpoint/kết quả.

FINISHED khóa room/match, ghi tất cả result và final snapshot/metadata/room cùng transaction. Retry thấy terminal thì dừng. Xếp hạng suy ra từ match_players JOIN matches FINISHED nên không có bước increment ngoài transaction để bị lặp.

## Kiểm thử tranh chấp

AuthSystemTest kiểm tra refresh đồng thời/replay/revoke qua filter và JDBC thật. BusinessFlowTest kiểm tra ghế cuối, một user vào hai phòng, kết toán đồng thời, snapshot cũ, deadline, pause/recovery, revision offline và quyền đọc lịch sử. Chạy cùng test trên PostgreSQL 18.6 cục bộ; CI dùng PostgreSQL service riêng.

## Giới hạn vận hành

Một instance game; không scale ngang khi chưa có ownership/distributed coordination. Chưa có benchmark p95/throughput hoặc query plan ở dữ liệu lớn. Kiểm thử đã chạy không có nghĩa không thể có deadlock dưới mọi tải.

Nếu thêm retry deadlock/serialization phải retry toàn transaction từ lớp ngoài, có giới hạn/backoff và idempotency. JPA/JDBC phải cùng datasource/transaction manager; JDBC write không tự refresh entity đã nạp. Không thêm vòng saveAndFlush từng player.
