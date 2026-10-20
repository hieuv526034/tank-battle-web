# 24. Bootstrap admin và phân quyền

Hệ thống cần tài khoản quản trị sau lần chạy đầu, tách với bốn người chơi mẫu local. Flyway V6 tạo hàng khóa BOOTSTRAP_ADMIN; ApplicationRunner tạo tài khoản sau khi schema sẵn sàng ở cả H2 và PostgreSQL.

## Cấu hình

| Biến | Ý nghĩa |
| --- | --- |
| BOOTSTRAP_ADMIN_ENABLED | Mặc định true; chỉ tắt rõ ràng cho môi trường không cần bootstrap. |
| BOOTSTRAP_ADMIN_USERNAME | Mặc định admin, qua chuẩn hóa/validator username chung. |
| BOOTSTRAP_ADMIN_DISPLAY_NAME | Mặc định Quản trị hệ thống, qua validator tên chung. |
| BOOTSTRAP_ADMIN_PASSWORD | 12–64 ký tự, chỉ dùng khi cần tạo tài khoản lần đầu. |

Local không nạp .env tự động: mật khẩu mẫu nằm trong application-local.yml, có thể ghi đè bằng biến môi trường trước lần tạo đầu. Production không có mật khẩu cố định trong source/migration: đã bổ sung biến vào .env/render.env, xem giá trị trực tiếp trong file. Import lại cấu hình vào Render trước deploy phiên bản này. Không dùng admin/admin vì mật khẩu đó không đạt hợp đồng validation hiện tại.

## Luồng và transaction

1. Flyway chạy V1–V6, JPA validate schema.
2. BootstrapAdminInitializer nhận cấu hình đã bind và bắt đầu một transaction.
3. Repository khóa BOOTSTRAP_ADMIN bằng SELECT FOR UPDATE, rồi đọc username.
4. Chưa có: kiểm mật khẩu, băm bằng PasswordEncoder PBKDF2 chung, INSERT một hàng với role ADMIN/enabled/audit.
5. Đã có admin enabled và hỗ trợ password: giữ nguyên hash/tên, không reset theo env.
6. Username trùng USER, admin bị khóa hoặc chỉ có Google: startup thất bại để người vận hành xử lý; không tự nâng quyền hoặc mở khóa.

Guard được giữ đến commit nên hai lần bootstrap đồng thời chỉ tạo một tài khoản. Unique username là lớp bảo vệ cuối. Không truy vấn/lưu theo vòng lặp, không ghi plaintext password xuống DB. Lỗi cấu hình bootstrap là lỗi startup kỹ thuật, không phải API BusinessException.

## Quyền API

Đăng nhập qua POST /api/auth/login với CSRF như tài khoản thường. Access JWT có roles=[ADMIN]. SecurityConfig dành /api/admin/** cho ROLE_ADMIN; USER nhận 403/AUTH_003 theo bundle vi/en. GET /api/admin/me trả id/username/displayName/role để kiểm tra quyền, không trả hash hoặc cấu hình bí mật. GET không cần CSRF.

ADMIN là vai trò quản trị hệ thống, không tự bỏ kiểm tra chủ sở hữu trong các API người chơi. Catalog địch/item/cửa hiện được cấu hình bằng JSON có phiên bản; chưa có CRUD API hoặc màn hình quản trị các catalog đó. Không coi việc đã có role là đã hoàn tất toàn bộ giao diện/chức năng quản trị.

## Chạy lại và kiểm tra

Restart với cùng DB không đổi mật khẩu. Nếu sửa BOOTSTRAP_ADMIN_PASSWORD sau khi tài khoản đã tồn tại, mật khẩu DB vẫn giữ nguyên; không dùng bootstrap để reset mật khẩu. Đổi username cấu hình sẽ tạo tài khoản admin mới nếu tên chưa tồn tại, vì vậy giữ username ổn định giữa các lần deploy.

AuthSystemTest kiểm login/JWT ADMIN, USER bị chặn, hai bootstrap đồng thời, chạy lại giữ hash/hồ sơ, không nâng USER và từ chối mật khẩu yếu. Cùng bộ test được chạy trên H2/PostgreSQL. Smoke prod profile dùng DB/key/password test riêng và kiểm Flyway cùng tài khoản admin, không đọc env production.
