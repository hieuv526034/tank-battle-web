# Neon là database production

Theo xác nhận của chủ dự án, Neon được cung cấp trong cuộc trao đổi là **production**, không dùng để chạy test.

## Định danh database/schema hiện tại

Yêu cầu mới nhất: **database `neondb`, schema `tank_battle`**. Đây là hai cấp khác nhau: JDBC URL giữ `/neondb`; `currentSchema=tank_battle` chọn schema. Không đổi tên package Java hoặc tên database test thành schema này.

Production cấu hình Flyway `default-schema/schemas=tank_battle`, `create-schemas=true`; lịch sử nằm trong `tank_battle.flyway_schema_history`. Hikari đặt schema=tank_battle trên mỗi kết nối JDBC, Hibernate dùng default_schema tương ứng. URL mẫu vẫn có currentSchema=tank_battle; pool không phụ thuộc việc Dashboard có tham số URL này. Khi schema chưa có, Flyway tạo trước, chạy V1–V6 rồi bootstrap admin. Nếu schema đã có bảng mà thiếu lịch sử Flyway, cần kiểm tra trạng thái trước, không tự baseline/clean/repair.

App và Flyway dùng **direct endpoint**, Hikari mặc định tối đa 5 kết nối. SQL JDBC/native hiện không ghi schema tại từng câu; Neon pooler ở transaction mode không đảm bảo SET search_path qua các transaction. Không chỉ thêm connection-init-sql rồi giữ pooler. [Hướng dẫn Neon về search_path](https://neon.com/docs/connect/connection-pooling#common-issues-with-set-statements).

Thay đổi default-schema không di chuyển dữ liệu/bảng cũ từ public. Nếu trước đây đã deploy vào public, cần kế hoạch chuyển schema và lịch sử cùng nhau sau backup; công việc này chưa thực hiện trên Neon. Ứng dụng đã bỏ H2 local; PostgreSQL integration và prod smoke dùng tank_battle để phát hiện lệch JDBC/JPA/Flyway.

## Cấu hình và kết quả kiểm tra

Đã kết nối JDBC qua SSL, chạy SELECT chỉ đọc: database `neondb`, PostgreSQL 18.6, schema `public` chưa có bảng tại thời điểm kiểm tra 20/10/2026. Không chạy ứng dụng/Flyway, không tạo schema/bảng/tài khoản thử trên Neon.

Thông tin kết nối thật nằm trong `.env` và `render.env`. Theo yêu cầu mới nhất của chủ đồ án ngày 20/10/2026, hai file này được phép commit để nhóm dùng chung cấu hình demo; `.gitignore` đã cho phép. [.env.example](../../.env.example) giữ mẫu cho máy phát triển. Spring hiện tự import .env ở working directory khi dùng prod; biến môi trường thật có ưu tiên cao hơn. Cách chạy IDE/bootRun và APP_ENV_FILE đọc [30](30-chay-local-va-nap-env.md). Docker vẫn loại env/key khỏi image; Render dùng Environment/Secret Files. Script không in giá trị vào log; RSA PEM trong secrets/ vẫn được phép commit theo quyết định chủ đồ án.

JDBC dùng `jdbc:postgresql://YOUR_NEON_DIRECT_HOST/neondb?sslmode=require&channelBinding=require&currentSchema=tank_battle`. Tên tham số của pgJDBC là `channelBinding`, khác `channel_binding` trong URI của libpq. Xem [tài liệu driver](https://jdbc.postgresql.org/documentation/use/). Đã pin pgJDBC 42.7.14 và Flyway 11.20.3 hỗ trợ PostgreSQL 18; hai module Flyway giữ cùng phiên bản.

## Kiểm tra lại chỉ đọc trên Windows

Sau khi chạy .\gradlew.bat copyPostgresDriver để xuất JDBC driver:

```powershell
.\tools\check-database.ps1
```

Script nạp biến môi trường và chạy `tools/CheckDatabase.java`. JDBC đặt readOnly, autocommit=false, chỉ SELECT metadata/tên bảng rồi rollback. Không in URL/password hoặc exception message của driver. Driver chỉ trả SQLSTATE nếu lỗi.

## Chuẩn bị chạy production sau này

1. Có máy chủ chạy ứng dụng và domain HTTPS; Neon chỉ cung cấp DB.
2. Tạo cặp RSA PKCS8/X509 ổn định, lưu trong secrets/ và cấu hình `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `AUTH_ISSUER` đúng URL public.
3. Bổ sung các biến còn thiếu trong `.env` từ `.env.example`. Profile prod mặc định bắt buộc keys ổn định; khóa tạm chỉ phục vụ test.
4. Khi chạy từ thư mục gốc, Spring tự đọc .env. Công cụ Java độc lập vẫn có thể nạp bằng `. .\tools\Import-LocalEnvironment.ps1`; script chỉ nạp KEY=VALUE vào process, không thực thi nội dung file. Trên Render import render.env thay cho .env local.
5. Ứng dụng mặc định profile prod, gồm cả khi chạy bootRun trên máy phát triển. Lần khởi đầu Flyway tự chạy V1–V6 tạo bảng và seed hệ thống; không dùng `ddl-auto=create/drop`.
6. Hikari giới hạn mặc định 5 kết nối, minimumIdle=0; dùng direct endpoint và schema tank_battle. Có thể chỉnh `DB_POOL_SIZE` sau khi đo thực tế.
7. Kiểm tra health/readiness, migration, HTTPS/cookie, WebSocket qua reverse proxy, Google callback và backup/restore trước demo public.

Không chạy ứng dụng để thử nghiệm trên Neon. Các test tự chọn profile test và DB tạm riêng; không nạp env production vào JVM test. Không còn profile local hoặc H2 trong runtime.

Render Free: [tài liệu 22](22-github-render-cicd.md) hướng dẫn env/Secret Files. SPRING_DATASOURCE_URL và SPRING_FLYWAY_URL cùng dùng direct endpoint/neondb/currentSchema=tank_battle. Docker build/CI không nhận thông tin production; start trên Render mới chạy migration.

## Database test tách biệt

[tools/test-postgres.ps1](../../tools/test-postgres.ps1) dùng Gradle tải binary PostgreSQL 18.6 portable từ Maven Central, tạo cluster tạm chỉ listen 127.0.0.1, chạy AuthSystemTest/BusinessFlowTest rồi dừng và xóa cluster đúng GUID. Không đọc `.env` và không cài service Windows. Binaries do [Zonky](https://github.com/zonkyio/embedded-postgres-binaries) đóng gói để kiểm thử.

```powershell
.\tools\test-postgres.ps1
```

Có thể dùng Docker riêng theo [compose.test.yml](../../compose.test.yml), hoặc PostgreSQL service trong [CI](../../.github/workflows/backend-ci.yml). Test chỉ nhận TEST_DATABASE_URL thuộc localhost/127.0.0.1/postgres; từ chối host Neon hoặc host bên ngoài.
