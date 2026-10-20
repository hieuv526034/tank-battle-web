# 23. Gradle và backend Spring

Project dùng Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.6/Groovy DSL. Thymeleaf là template engine phía server, không phụ thuộc Maven/Gradle. Backend hiện không cần Node/npm hoặc package.json.

## Build script và IDE

[build.gradle](../../build.gradle) quản lý plugin/dependency/toolchain/format/test/JAR. [settings.gradle](../../settings.gradle) giữ tên project; [gradle.properties](../../gradle.properties) quản lý heap/cache. Commit [Wrapper properties](../../gradle/wrapper/gradle-wrapper.properties), [Wrapper JAR](../../gradle/wrapper/gradle-wrapper.jar), [gradlew](../../gradlew) và [gradlew.bat](../../gradlew.bat). Distribution có SHA-256; không cần cài Gradle riêng.

Đã bỏ hai import TestDescriptor/TestResult và listener afterSuite chỉ để in số test. Những kiểu này vốn thuộc Gradle API, không phải dependency JUnit của ứng dụng. Gradle có sẵn báo cáo tổng kết XML/HTML nên không cần listener riêng.

IntelliJ: Open thư mục gốc/build.gradle → Load Gradle Project → Distribution Wrapper → Gradle JVM 21 → Reload. Mở một thư mục như project Java/Maven thường sẽ khiến DSL/plugin/import của Gradle bị báo đỏ dù Wrapper build được. Không tự import org.junit.platform.engine.TestDescriptor vào build.gradle.

## Lệnh Windows

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean build
.\gradlew.bat bootJar
.\gradlew.bat test --tests "*SourceContractTest"
.\gradlew.bat copyPostgresDriver
.\tools\test-postgres.ps1 -ProductionSmoke
.\gradlew.bat bootRun "--args=--spring.profiles.active=local"
```

Linux/macOS dùng ./gradlew; lần đầu chmod +x gradlew. Cần JDK 21; không tự tải JDK. build gồm Spotless, test và executable JAR. bootJar chỉ đóng gói, không thay bước test. Nếu chạy JAR trong build/libs, dừng đúng ứng dụng đó trước clean build để Windows giải phóng file.

## Test và đầu ra

- JAR: build/libs/tank-battle-web-1.0.0.jar.
- JUnit thường: build/reports/tests/test/index.html và build/test-results/test.
- PostgreSQL: build/reports/tests/postgresTest/index.html và build/test-results/postgresTest.
- Script PostgreSQL/smoke: log ở build/verification, binary/cluster tạm dưới build/postgres-test.
- Gradle cache ở thư mục chuẩn của người dùng; không tạo .tools. clean xóa build, không xóa .data/secrets hoặc Neon.

SourceContractTest bảo vệ catalog/bundle, package/module boundary, quy ước kiểu Java/Instant, exception ValidateCode, link tài liệu và cách loại env/key khỏi Docker. Test backend nằm trong src/test/java; không duy trì một bộ Node tests riêng.

postgresTest chọn AuthSystemTest/BusinessFlowTest và luôn chạy thật, không dùng cache kết quả DB. Task yêu cầu TEST_DATABASE_URL PostgreSQL; lớp hỗ trợ test chặn host bên ngoài. Script Windows tự tạo PostgreSQL portable trên loopback; CI Linux dùng service PostgreSQL riêng. Phải build JAR trước khi gọi -ProductionSmoke.

## CI, Docker và DB

GitHub gọi Wrapper, kiểm Java/H2/PostgreSQL rồi build/smoke Docker. Docker build không chạy Spring/Flyway hoặc kết nối Neon. JDK 21 build JAR, JRE 21 chạy artifact. Render khởi profile prod mới kết nối Neon và áp migration còn thiếu.

| Nơi chạy | DB |
| --- | --- |
| Local | H2 file .data |
| JUnit thường | H2 test riêng |
| CI verify | PostgreSQL tankbattle_test tạm |
| CI container | PostgreSQL tankbattle_smoke tạm |
| Render | Neon production qua biến môi trường |

Xem [Render](22-github-render-cicd.md), [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md) và [STATUS](STATUS.md). .env/render.env được phép commit theo yêu cầu chủ đồ án; Docker vẫn loại chúng khỏi image. RSA/H2 giữ riêng. Chưa nâng Gradle 9 hoặc scale nhiều runtime instance.
