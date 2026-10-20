# Hồ sơ source UTC Tank Battle

Đây là nơi lưu quy ước, thiết kế, luồng xử lý, bằng chứng kiểm thử và bàn giao để người/AI khác tiếp tục source. Backend Java 21/Spring Boot 3.5.16/Gradle 8.14.6; package vn.edu.utc.tankbattle. UI/Canvas và offline browser chưa triển khai.

## Thứ tự đọc

1. [AGENTS.md](AGENTS.md): yêu cầu phải giữ và quy tắc sửa code.
2. [STATUS](docs/STATUS.md): trạng thái thực tế, test đã chạy và giới hạn.
3. [HANDOFF](docs/HANDOFF.md): bước tiếp tục, các quyết định và điều dễ sửa sai.
4. [Quy hoạch package](docs/17-quy-hoach-package.md), [luồng backend](docs/20-luong-backend-va-test.md), [bản đồ cấu hình/service](docs/21-doc-code-cau-hinh-service.md).
5. [API/Swagger](docs/11-swagger-va-api.md), [auth](docs/09-auth-jwt-google.md), [bootstrap admin](docs/24-bootstrap-admin-va-phan-quyen.md).
6. [Gradle](docs/23-gradle-va-thymeleaf.md), [Render/CI](docs/22-github-render-cicd.md), [Neon](docs/19-neon-production.md).

Các tài liệu 00–08 là kế hoạch/thiết kế gốc, không thay thế STATUS. Tài liệu 10/16 mô tả độ khó, cửa, địch/item/điểm; 12/13 mô tả lỗi/validation/transaction; 14/15 mô tả Instant và H2/seed.

## Bản đồ source

```text
src/main/java/vn/edu/utc/tankbattle/
  auth/       tài khoản, JWT/Google, security và bootstrap admin
  common/     base, enum/mã lỗi, validation, i18n, utility và event
  business/   controller/service/repository/entity/dto/mapper/model dùng chung
              config/engine/runtime/websocket
src/main/resources/  cấu hình, Flyway V1–V6, bundle và catalog JSON
src/test/java/       JUnit API/auth/game/storage/concurrency/source contracts
tools/               script PostgreSQL test, kiểm DB chỉ đọc, JWT và CI/Render
.agents/             tài liệu và bàn giao
```

Engine tick chỉ dùng RAM; checkpoint/kết toán ghi theo lô ngoài tick. JPA phục vụ CRUD, JDBC giữ projection/khóa/batch; mốc sự kiện dùng Instant. Online giữ matchId sau khôi phục; offline không cộng ranking online. Một runtime instance, chưa có ownership xuyên process.

## Lệnh chính

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean build
.\tools\test-postgres.ps1 -ProductionSmoke
.\gradlew.bat bootRun # Mặc định prod; cần biến DB/JWT/admin trước khi chạy.
```

Chỉ cần JDK 21; Wrapper tải Gradle/dependency. Không có Node/npm trong backend. Báo cáo JUnit dưới build/reports/tests; log PostgreSQL/smoke ở build/verification. Ứng dụng mặc định prod/PostgreSQL; H2 chỉ trong test. Dữ liệu H2 cũ trong .data và RSA trong secrets không bị xóa khi dọn build.

Theo yêu cầu chủ đồ án, .env/render.env và toàn bộ secrets/ gồm cặp JWT PEM được phép commit; không in giá trị/key vào tài liệu/log. Docker loại env/key khỏi image. Render vẫn cần import env và thêm Secret Files. Không dùng Neon production chạy test.

Database production neondb/schema tank_battle; direct endpoint cùng Hikari 5 kết nối, JDBC/JPA/Flyway thống nhất schema. Bản kiểm chứng mới nhất: 79 test Java, 35 test PostgreSQL 18.6 và prod-profile JAR trên DB test mới; admin login/API quản trị/API phòng đạt. Đọc STATUS trước khi dùng số liệu này cho phiên bản source khác.
