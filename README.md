# UTC Tank Battle

Game xe tăng với giao diện tiếng Việt, phòng chơi online và solo trong trình duyệt. Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.6, Thymeleaf và PostgreSQL. Ứng dụng chỉ dùng môi trường `prod`; H2 chỉ dành cho test. Package `vn.edu.utc.tankbattle`.

## Giao diện

- Trang chủ, đăng ký/đăng nhập, hồ sơ, phòng chờ, bảng hạng và lịch sử.
- Canvas online qua WebSocket; solo Arena/Campaign bằng engine JavaScript.
- Ba bản lưu IndexedDB, đồng bộ đám mây có kiểm tra phiên bản, tài nguyên offline qua Service Worker.
- Giao diện sáng/tối/theo thiết bị, thích ứng điện thoại; Lucide và font Be Vietnam Pro được đóng gói sẵn, không cần CDN hoặc Node/npm.

Mở `/` sau khi chạy ứng dụng. Chờ “Đã sẵn sàng offline” trước khi dùng `/offline` khi mất mạng. Dữ liệu trên máy thuộc trình duyệt; đăng nhập để đồng bộ đám mây. Điều khiển và giới hạn kiểm thử đọc [hướng dẫn UI](.agents/docs/25-giao-dien-va-offline.md). Đã kiểm tra chơi/lưu/khôi phục, phòng online và kết quả trên H2 tạm; chi tiết luồng, tối ưu và giới hạn thiết bị/production trong [tài liệu 27](.agents/docs/27-hinh-anh-hieu-nang-va-luong-game.md).

## Build và chạy

```powershell
.\gradlew.bat clean build
# Sau khi nạp biến DB/JWT/admin; mặc định prod, khởi động sẽ chạy Flyway trên DB đã chọn.
.\gradlew.bat bootRun
```

JAR: `build/libs/tank-battle-web-1.0.0.jar`, đóng gói bằng `bootJar` để bỏ qua test. Swagger `/swagger-ui.html` khi `API_DOCS_ENABLED=true`. Cần biến môi trường DB/JWT/admin trước khi chạy; Spring không tự đọc `.env`. Không cần Node/npm. Linux/macOS dùng `./gradlew`.

Tài liệu, trạng thái kiểm chứng và hướng dẫn AI tiếp tục: [.agents/README.md](.agents/README.md). Deploy GitHub/Render: [.agents/docs/22-github-render-cicd.md](.agents/docs/22-github-render-cicd.md).

Theo yêu cầu chủ đồ án, `.env`/`render.env` và toàn bộ `secrets/` (gồm cặp JWT PEM) được phép commit. Docker loại env/key khỏi image; dữ liệu H2 vẫn bị Git bỏ qua. Render vẫn cần import env và thêm Secret Files.

Production: Neon database `neondb`, schema `tank_battle`, direct endpoint cùng Hikari pool nhỏ. Flyway tạo schema/bảng và bootstrap admin từ cấu hình lần đầu; restart không reset dữ liệu hay mật khẩu.
