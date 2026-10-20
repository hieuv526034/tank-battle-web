# H2 local, migration và dữ liệu mặc định

Local dùng H2 file .data/tankbattle.mv.db trong working directory. Tắt app không xóa dữ liệu; H2 memory chỉ dùng test. Đặt LOCAL_DB_PATH tuyệt đối nếu chạy IDE và terminal khác thư mục; không thêm đuôi .mv.db vào biến.

```powershell
$env:LOCAL_DB_PATH = "C:/du-an/tank-battle-web/.data/tankbattle"
.\gradlew.bat bootRun "--args=--spring.profiles.active=local"
```

URL có MODE=PostgreSQL, DATABASE_TO_LOWER, DEFAULT_NULL_ORDERING=HIGH và WRITE_DELAY=0. Mode tương thích không thay thế test PostgreSQL. Xem [H2 URL](https://h2database.com/html/features.html#database_url).

## Tạo schema và seed

Flyway hiện có thêm V6 tạo guard BOOTSTRAP_ADMIN. Sau migration, BootstrapAdminInitializer tạo tài khoản ADMIN từ cấu hình khi chưa có; không đặt mật khẩu vào SQL. Local có mẫu trong application-local.yml, production lấy BOOTSTRAP_ADMIN_PASSWORD. Đây là dữ liệu hệ thống, không phụ thuộc LOCAL_DEMO_ENABLED. Xem [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).

| Migration | Nội dung                                                                           |
| --------- | ---------------------------------------------------------------------------------- |
| V1        | users, rooms, room_players, matches, match_players, match_snapshots và audit/index |
| V2        | external_identities, auth_sessions, auth_refresh_tokens; role/enabled mặc định     |
| V3        | app_locks và hàng CREATE_ROOM                                                      |
| V4        | game_mode/difficulty/stage, kills/score, offline_saves                             |
| V5        | Giữ revision sau xóa slot, hủy payload và chặn thiết bị cũ ghi đè bản tạo lại      |

Flyway chạy trước khi ứng dụng sẵn sàng và ghi checksum/version vào flyway_schema_history. Lần sau chỉ áp migration mới; thêm thay đổi qua V6 trở đi, không sửa migration đã dùng trên DB chia sẻ.

JPA ddl-auto=validate, sql.init.mode=never, Flyway clean-disabled=true. Không chạy create-drop hoặc data.sql mỗi lần restart. Catalog game mặc định nằm trong JSON có phiên bản; không nhân đôi thành bảng config khi chưa có màn hình quản trị.

## Tài khoản demo tùy chọn

```powershell
$env:LOCAL_DEMO_ENABLED = "true"
.\gradlew.bat bootRun "--args=--spring.profiles.active=local"
```

Tạo utc_demo1..utc_demo4 với mật khẩu ví dụ local UtcDemo!2026; đổi qua LOCAL_DEMO_PASSWORD trước seed đầu. Chỉ profile local và không prod. Một query tìm username hiện có, một batch thêm những tài khoản thiếu; không ghi đè tên/hash đã đổi. Mặc định seed demo tắt. Production không có admin/mật khẩu mặc định.

## Dữ liệu không mất

Giữ file .data cùng đường dẫn khi chạy lại. Nếu xóa .data, đổi working directory hoặc LOCAL_DB_PATH sang nơi khác thì đang mở DB khác/trống. Backup H2 khi app đã dừng, hoặc dùng cơ chế backup phù hợp; không copy file đang ghi rồi coi như backup đã bảo đảm.

H2FilePersistenceTest dùng TempDir riêng: migrate/seed → sửa tên/hash → lưu auth session Instant → đóng pool → mở lại cùng file → migrate/seed lần nữa → kiểm tra giữ dữ liệu, chỉ bốn tài khoản, một CREATE_ROOM và năm migration. Không chạm .data thật.

Checkpoint/kết quả online ở DB; dữ liệu trong RAM giữa hai checkpoint có thể mất khi crash. Mặc định khoảng 2 giây nếu storage khỏe. Offline browser cần IndexedDB/Service Worker ở UI tương lai; H2 trên server không thay thế storage tại máy người chơi.

Neon đã được xác nhận là production; đọc [tài liệu 19](19-neon-production.md). Test dùng H2 memory/TempDir hoặc PostgreSQL 18.6 loopback, không dùng Neon.
