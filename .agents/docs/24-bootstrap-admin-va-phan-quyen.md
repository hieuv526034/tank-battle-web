# 24. Bootstrap admin và phân quyền

Hệ thống cần tài khoản quản trị sau lần chạy đầu. Flyway V6 tạo hàng khóa BOOTSTRAP_ADMIN; ApplicationRunner INSERT tài khoản thật vào users sau khi schema sẵn sàng. Ứng dụng chỉ chạy prod/PostgreSQL; H2 dành riêng cho test. Bộ tạo bốn người chơi mẫu local đã bỏ.

## Cấu hình

| Biến | Ý nghĩa |
| --- | --- |
| BOOTSTRAP_ADMIN_ENABLED | Mặc định true; chỉ tắt rõ ràng cho môi trường không cần bootstrap. |
| BOOTSTRAP_ADMIN_USERNAME | Mặc định admin, qua chuẩn hóa/validator username chung. |
| BOOTSTRAP_ADMIN_DISPLAY_NAME | Mặc định Quản trị hệ thống, qua validator tên chung. |
| BOOTSTRAP_ADMIN_PASSWORD | 12–64 ký tự, chỉ dùng khi cần tạo tài khoản lần đầu. |

Ứng dụng không nạp .env tự động và không có mật khẩu mẫu trong cấu hình runtime. BOOTSTRAP_ADMIN_PASSWORD lấy từ môi trường; test có cấu hình/DB riêng trong src/test/resources/application-test.yml: đã bổ sung biến vào .env/render.env, xem giá trị trực tiếp trong file. Import lại cấu hình vào Render trước deploy phiên bản này. Không dùng admin/admin vì mật khẩu đó không đạt hợp đồng validation hiện tại.

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

## Lỗi cấu hình trên Render

Nếu Flyway/Hibernate/Tomcat đã khởi tạo nhưng log có `BOOTSTRAP_ADMIN_PASSWORD cần 12–64 ký tự khi tạo admin lần đầu`, runner chưa tạo được admin do giá trị môi trường không hợp lệ. Vào Environment của service Render, thêm/cập nhật đúng `BOOTSTRAP_ADMIN_PASSWORD` bằng mật khẩu hợp lệ trong render.env, giữ `BOOTSTRAP_ADMIN_ENABLED=true` và username dự định, rồi Save/redeploy. Không đưa mật khẩu vào log/tài liệu.

Spring không tự đọc render.env trong repository, và push file lên Git không tự cập nhật Environment của service tạo thủ công. Hai PEM vẫn được cung cấp qua Secret Files. Dòng Tomcat/Started có thể xuất hiện trước khi ApplicationRunner xong; cần chờ readiness UP và thử đăng nhập admin sau deploy. Không tắt bootstrap hoặc đổi ddl-auto để xử lý lỗi cấu hình mật khẩu.

## Chạy lại và kiểm tra

Restart với cùng DB không đổi mật khẩu. Nếu sửa BOOTSTRAP_ADMIN_PASSWORD sau khi tài khoản đã tồn tại, mật khẩu DB vẫn giữ nguyên; không dùng bootstrap để reset mật khẩu. Đổi username cấu hình sẽ tạo tài khoản admin mới nếu tên chưa tồn tại, vì vậy giữ username ổn định giữa các lần deploy.

AuthSystemTest kiểm login/JWT ADMIN, USER bị chặn, hai bootstrap đồng thời, chạy lại giữ hash/hồ sơ, không nâng USER và từ chối mật khẩu yếu. Cùng bộ test được chạy trên H2/PostgreSQL. Smoke prod profile dùng DB/key/password test riêng và kiểm Flyway cùng tài khoản admin, không đọc env production.
