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
.\gradlew.bat bootRun # Mặc định prod; cần biến DB/JWT/admin trước khi chạy.
```

Linux/macOS dùng ./gradlew; lần đầu chmod +x gradlew. Cần JDK 21; không tự tải JDK. build gồm Spotless, test và executable JAR. bootJar chỉ đóng gói, không thay bước test. Nếu chạy JAR trong build/libs, dừng đúng ứng dụng đó trước clean build để Windows giải phóng file.

## Phiên bản Flyway và schema

Boot BOM vẫn quản lý dependency chung; build.gradle ghi đè flyway.version=11.20.3 cho cả flyway-core và flyway-database-postgresql, hỗ trợ PostgreSQL 18. Giữ nguyên checksum V1–V6. [Mã nguồn hỗ trợ PostgreSQL của Flyway 11.20.3](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java).

Production dùng Hikari schema=tank_battle, Flyway tự tạo/migrate schema, Hibernate validate. @Index/@UniqueConstraint trong entity mô tả index/ràng buộc đã được migration tạo; sửa annotation không tự nâng DDL production. ProductionProfileTest kiểm binding pool thật mà không nối DB; prod smoke dùng URL không có currentSchema và đăng nhập admin/gọi rooms API để kiểm JDBC.

## Test và đầu ra

- JAR: build/libs/tank-battle-web-1.0.0.jar.
- JUnit thường: build/reports/tests/test/index.html và build/test-results/test.
- PostgreSQL: build/reports/tests/postgresTest/index.html và build/test-results/postgresTest.
- Script PostgreSQL/smoke: log ở build/verification, binary/cluster tạm dưới build/postgres-test.
- Gradle cache ở thư mục chuẩn của người dùng; không tạo .tools. clean xóa build, không xóa .data/secrets hoặc Neon.

SourceContractTest bảo vệ catalog/bundle, package/module boundary, quy ước kiểu Java/Instant, exception ValidateCode, link tài liệu và cách loại env/key khỏi Docker. Test backend nằm trong src/test/java; không duy trì một bộ Node tests riêng.

postgresTest chọn AuthSystemTest/BusinessFlowTest và luôn chạy thật, không dùng cache kết quả DB. Task yêu cầu TEST_DATABASE_URL PostgreSQL; lớp hỗ trợ test chặn host bên ngoài. Script Windows tự tạo PostgreSQL portable trên loopback; CI Linux dùng service PostgreSQL riêng. Phải build JAR trước khi gọi -ProductionSmoke.

## CI, Docker và DB

Docker dùng `./gradlew --no-daemon --console=plain bootJar`: chỉ compile main và đóng gói, không compile/chạy test, không khởi động Spring và không kết nối DB. Mọi build đều dùng PostgreSQL; không còn cờ -Pproduction, driver H2 runtime hoặc application-local.yml. H2 chỉ trong testRuntimeOnly. .dockerignore loại src/test khỏi build context.

application.yml đặt spring.profiles.default=prod; application-prod.yml giữ cấu hình PostgreSQL/HTTPS. Google là phần bổ sung OAuth; test chỉ nằm trong src/test, không đóng gói vào JAR. Runtime Docker đặt mặc định `SPRING_PROFILES_ACTIVE=prod`; Render giữ cùng biến `prod` (hoặc `prod,google` khi đã cấu hình Google), không ghi đè thành local. Profile là lựa chọn khi chạy ứng dụng, không phải khi biên dịch Java. Stage cuối chỉ có JRE/JAR; Gradle/JDK/cache/build outputs không đi theo runtime. Bộ nhớ build và JVM ứng dụng là hai giai đoạn riêng; driver H2 nằm trong JAR cũ không đồng nghĩa đã mở H2. Chưa có số đo RAM thực tế trên Render.

Khi khởi động prod lần đầu, Flyway tạo schema/bảng còn thiếu và bootstrap tạo admin theo BOOTSTRAP_ADMIN_* sau Flyway. Cần cả DB env và cặp RSA Secret Files; chỉ đổi profile không sửa được lỗi thiếu `/etc/secrets/jwt-public.pem`. Bootstrap không reset tài khoản đã có. Không đưa credential production vào bước build để khởi tạo DB.

GitHub gọi Wrapper, kiểm Java/H2/PostgreSQL rồi build/smoke Docker. Docker build không chạy Spring/Flyway hoặc kết nối Neon. JDK 21 build JAR, JRE 21 chạy artifact. Render khởi profile prod mới kết nối Neon và áp migration còn thiếu.

| Nơi chạy | DB |
| --- | --- |
| Chạy ứng dụng trên máy phát triển | PostgreSQL qua env, mặc định prod; có thể áp Flyway trên DB đã chọn |
| JUnit thường | H2 test riêng |
| CI verify | PostgreSQL tankbattle_test tạm |
| CI container | PostgreSQL tankbattle_smoke tạm |
| Render | Neon production qua biến môi trường |

Xem [Render](22-github-render-cicd.md), [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md) và [STATUS](STATUS.md). .env/render.env và toàn bộ secrets/ gồm cặp JWT PEM được phép commit theo yêu cầu chủ đồ án; Docker vẫn loại env/key khỏi image và Render dùng Secret Files. Dữ liệu H2 vẫn bị Git bỏ qua. Chưa nâng Gradle 9 hoặc scale nhiều runtime instance.

## Tăng tốc GitHub CI

- Hai job verify và docker chạy song song trên runner/DB tạm riêng. deploy-ready vẫn chờ cả hai và chỉ thành công khi cả hai success; Render tiếp tục After CI Checks Pass.
- verify dùng gradle/actions/setup-gradle@v5 để cache Wrapper/dependency/build state, bỏ cache: gradle ở setup-java để không có hai cơ chế cache cùng Gradle User Home.
- Gọi build postgresTest trong cùng một lần Gradle, bỏ clean trên runner mới. Task test lọc TEST_DATABASE_* khỏi môi trường JVM con nên vẫn dùng H2 riêng; postgresTest giữ nguyên env PostgreSQL CI và luôn chạy lại, không dùng cache kết quả DB.
- Khai báo source/docs mà SourceContractTest đọc thành task inputs để cache không bỏ sót thay đổi hợp đồng source.
- Docker dùng setup-buildx-action@v4/build-push-action@v7, load:true để chạy smoke ngay tại runner, cache-from/cache-to type=gha,mode=max. Cache export có ignore-error=true vì lỗi lưu cache không làm hỏng kết quả image đã build. Không push image lên registry, không thêm secret production.
- Cache GitHub không chia sẻ trực tiếp sang Render. Render vẫn tự build Dockerfile và có cache riêng. Lần đầu phải tạo cache; chưa có số đo thời gian trước/sau trên GitHub.

Tham chiếu: [Gradle caching](https://github.com/gradle/actions/blob/v5/docs/setup-gradle.md), [Docker GitHub cache](https://docs.docker.com/build/ci/github-actions/cache/).
