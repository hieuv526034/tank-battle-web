# UTC Tank Battle Backend

Backend Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.6, Thymeleaf và PostgreSQL. Ứng dụng chỉ dùng môi trường `prod`; H2 chỉ dành cho test. Package `vn.edu.utc.tankbattle`. UI/Canvas và offline browser chưa triển khai.

```powershell
.\gradlew.bat clean build
# Sau khi nạp biến DB/JWT/admin; mặc định prod, khởi động sẽ chạy Flyway trên DB đã chọn.
.\gradlew.bat bootRun
```

JAR: `build/libs/tank-battle-web-1.0.0.jar`, đóng gói bằng `bootJar` để bỏ qua test. Swagger `/swagger-ui.html` khi `API_DOCS_ENABLED=true`. Cần biến môi trường DB/JWT/admin trước khi chạy; Spring không tự đọc `.env`. Không cần Node/npm. Linux/macOS dùng `./gradlew`.

Tài liệu, trạng thái kiểm chứng và hướng dẫn AI tiếp tục: [.agents/README.md](.agents/README.md). Deploy GitHub/Render: [.agents/docs/22-github-render-cicd.md](.agents/docs/22-github-render-cicd.md).

Theo yêu cầu chủ đồ án, `.env`/`render.env` và toàn bộ `secrets/` (gồm cặp JWT PEM) được phép commit. Docker loại env/key khỏi image; dữ liệu H2 vẫn bị Git bỏ qua. Render vẫn cần import env và thêm Secret Files.

Production: Neon database `neondb`, schema `tank_battle`, direct endpoint cùng Hikari pool nhỏ. Flyway tạo schema/bảng và bootstrap admin từ cấu hình lần đầu; restart không reset dữ liệu hay mật khẩu.
