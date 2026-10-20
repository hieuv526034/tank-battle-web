# UTC Tank Battle Backend

Backend Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.6, Thymeleaf và PostgreSQL/H2. Package `vn.edu.utc.tankbattle`. UI/Canvas và offline browser chưa triển khai.

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun "--args=--spring.profiles.active=local"
```

JAR: `build/libs/tank-battle-web-1.0.0.jar`. Swagger local: `/swagger-ui.html`. H2 local lưu tại `.data`; không cần Node/npm. Linux/macOS dùng `./gradlew`.

Tài liệu, trạng thái kiểm chứng và hướng dẫn AI tiếp tục: [.agents/README.md](.agents/README.md). Deploy GitHub/Render: [.agents/docs/22-github-render-cicd.md](.agents/docs/22-github-render-cicd.md).

Theo yêu cầu chủ đồ án, `.env`/`render.env` được phép commit. Docker loại env khỏi image; khóa RSA và dữ liệu H2 giữ riêng. Render vẫn cần import env và thêm Secret Files.

Production: Neon database `neondb`, schema `tank_battle`, direct endpoint cùng Hikari pool nhỏ. Flyway tạo schema/bảng và bootstrap admin từ cấu hình lần đầu; restart không reset dữ liệu hay mật khẩu.
