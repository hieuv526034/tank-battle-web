# Database, migration và dữ liệu đăng nhập ban đầu

Ứng dụng chỉ dùng PostgreSQL với profile mặc định prod. application-local.yml, driver H2 runtime và bộ seed bốn tài khoản demo local đã bỏ theo yêu cầu mới nhất. H2 chỉ nằm trong testRuntimeOnly, dùng DB memory/TempDir riêng. Dữ liệu H2 cũ trong .data không bị xóa hoặc tự chuyển sang PostgreSQL.

## Tạo schema và tài khoản admin thật

1. Nạp biến SPRING_DATASOURCE_*, SPRING_FLYWAY_URL, JWT_PRIVATE_KEY/JWT_PUBLIC_KEY và BOOTSTRAP_ADMIN_* trước khi khởi động. Spring không tự đọc .env; Docker build không kết nối DB.
2. Khi chạy ứng dụng, Flyway tạo schema tank_battle và các bảng còn thiếu, lưu lịch sử migration trong tank_battle.flyway_schema_history.
3. BootstrapAdminInitializer chạy transaction sau Flyway, khóa guard BOOTSTRAP_ADMIN, kiểm username và INSERT admin nếu chưa có. Mật khẩu từ BOOTSTRAP_ADMIN_PASSWORD được băm PBKDF2; role ADMIN, enabled=true, có audit.
4. Tài khoản đã lưu được dùng đăng nhập qua POST /api/auth/login và nhận JWT để gọi API. Xem [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md) và [API](11-swagger-va-api.md).
5. Restart không INSERT trùng, không reset mật khẩu hoặc ghi đè hồ sơ. Không tự nâng USER trùng tên thành ADMIN.

| Migration | Nội dung |
| --- | --- |
| V1 | users, rooms, room_players, matches, match_players, match_snapshots và audit/index |
| V2 | external_identities, auth_sessions, auth_refresh_tokens; role/enabled mặc định |
| V3 | app_locks và hàng CREATE_ROOM |
| V4 | game_mode/difficulty/stage, kills/score, offline_saves |
| V5 | Giữ revision sau xóa slot, hủy payload và chặn thiết bị cũ ghi đè |
| V6 | Hàng khóa BOOTSTRAP_ADMIN để bootstrap đồng thời không INSERT trùng |

Flyway chỉ áp migration chưa chạy. Thay đổi schema tiếp theo qua V7 trở đi, không sửa V1–V6 đã phát hành. JPA ddl-auto=validate, sql.init.mode=never, Flyway clean-disabled=true. Catalog gameplay vẫn ở JSON có phiên bản; chưa có CRUD quản trị catalog.

## Dữ liệu và kiểm thử

PostgreSQL/Neon lưu tài khoản, phiên, phòng, trận, checkpoint và save cloud. Redeploy không reset DB. Không tự di chuyển dữ liệu hoặc baseline lịch sử khi đổi schema; xem [Neon](19-neon-production.md).

H2FilePersistenceTest dùng TempDir: migrate/bootstrap admin → sửa tên/hash → lưu auth session Instant → đóng/mở pool → migrate/bootstrap lại → kiểm chỉ một admin, giữ tên/hash/session và đủ sáu migration. AuthSystemTest kiểm tài khoản bootstrap đăng nhập/nhận JWT ADMIN/truy cập API quản trị; test dùng DB riêng, không dùng Neon.

Checkpoint/kết quả online ở DB; crash có thể mất đoạn từ checkpoint cuối. Offline browser cần IndexedDB/Service Worker ở UI tương lai, không dựa vào DB server làm storage trên máy người chơi.
