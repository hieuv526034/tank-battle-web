# 30. Chạy source local và nạp cấu hình .env

Cập nhật 21/10/2026. Áp dụng cho IDE, Gradle bootRun và executable JAR. Giữ Java 21,
profile prod/PostgreSQL; không đưa profile local hoặc H2 vào runtime.

## Nguyên nhân lỗi placeholder

`Could not resolve placeholder 'SPRING_DATASOURCE_URL'` xuất hiện khi tiến trình Java chưa nhận
biến DB. Có file .env không có nghĩa Spring tự đọc file đó. Trước lượt sửa này README chỉ nói
nạp env trước, nên nhấn Run trong IDE mà chưa cấu hình env vẫn lỗi. Biểu thức Flyway
`${SPRING_FLYWAY_URL:${SPRING_DATASOURCE_URL}}` đúng: dùng URL riêng hoặc fallback URL datasource.
Không sửa biểu thức thành giá trị rỗng, không tắt Flyway và không đổi sang H2 để né lỗi.

Sau khi có import vẫn có thể gặp lại lỗi nếu working directory khác gốc project: file import là
optional nên file bị bỏ qua, tới Flyway mới báo thiếu placeholder. Đã kiểm đọc ConfigData bằng JVM
không có auto-configuration/DB: cùng classpath, prod tường minh hoặc mặc định đều tìm được URL
khi chạy tại gốc; đổi working directory sang build/classes/java/main thì tái hiện lỗi thiếu biến.
Cấu hình Run riêng của IntelliJ trước lượt sửa chưa khai báo working directory; chưa thu được
working directory thực tế của lần chạy lỗi nên không khẳng định đó là nguyên nhân duy nhất.

## Cách nạp hiện tại

application-prod.yml khai báo:

```yaml
spring:
  config:
    import: "optional:file:${APP_ENV_FILE:./.env}[.properties]"
```

1. Mặc định đọc .env trong working directory bằng bộ đọc properties sẵn có của Spring Boot.
2. APP_ENV_FILE chọn file khác; dùng đường dẫn slash `/`, không thêm tiền tố file: vì import đã có.
   Đường dẫn tương đối tính từ working directory, đường dẫn tuyệt đối hỗ trợ thư mục có dấu cách.
3. optional nghĩa là không lỗi vì thiếu file. Nếu cả file và biến DB đều thiếu, cấu hình DB vẫn lỗi
   như trước; không tự dùng database khác hoặc mật khẩu mặc định.
4. Biến môi trường thật/JVM arguments/command line vẫn có quyền ghi đè file. Trên Render .env không
   vào image, các biến Dashboard và Secret Files tiếp tục được dùng. Không đổi render.env hoặc PEM.
5. Chỉ prod import .env. JUnit profile test không đọc file thật; ProductionProfileTest dùng fixture.
   Smoke JAR prod dùng .env tạm trong working directory riêng, không truyền APP_ENV_FILE hoặc
   DB/JWT vào môi trường tiến trình con; chỉ PostgreSQL loopback, không đọc .env gốc/Neon.

.env viết KEY=VALUE nguyên bản. Không thêm export, lệnh shell hoặc bọc giá trị bằng dấu nháy.
Đường dẫn Windows trong file dùng file:./secrets/... hoặc slash `/` để tránh escape backslash
của định dạng properties. Script Import-LocalEnvironment vẫn dùng được khi cần nạp cho công cụ
ngoài Spring, nhưng không bắt buộc trước bootRun/IDE nữa.

## Cấu hình local đã bổ sung

Không đổi URL, username, password DB hoặc bootstrap password đang có trong .env. Chỉ thêm các khóa
còn thiếu phục vụ máy phát triển:

```properties
AUTH_ISSUER=http://localhost:8080
JWT_PRIVATE_KEY=file:./secrets/jwt-private.pem
JWT_PUBLIC_KEY=file:./secrets/jwt-public.pem
AUTH_SECURE_COOKIE=false
SESSION_SECURE_COOKIE=false
```

Dùng cặp RSA ổn định hiện có, không sinh lại key. Hai cờ cookie false dành cho HTTP local;
application.yml/application-prod.yml vẫn mặc định true nếu không khai báo. Render dùng HTTPS và
render.env riêng, không lấy các cờ false của local vì Docker không chứa .env.

## Chạy trong terminal hoặc IDE

Từ thư mục gốc project:

```powershell
.\gradlew.bat bootRun
```

Hoặc sau khi build:

```powershell
.\gradlew.bat build
java -jar .\build\libs\tank-battle-web-1.0.0.jar
```

IntelliJ: dùng cấu hình **TankBattleApplication (prod local)** được lưu trong
[.run/TankBattleApplication-prod-local.run.xml](../../.run/TankBattleApplication-prod-local.run.xml).
Cấu hình chia sẻ này chọn module tank-battle-web.main/JDK 21, main class TankBattleApplication,
Active profiles = prod và Working directory = `$PROJECT_DIR$` (thư mục có build.gradle/.env/secrets).
Sau khi IDE nhận file .run mới, chọn đúng tên trên thanh Run rồi chạy. Nếu chưa thấy, reload project
hoặc tạo Spring Boot Run Configuration với đúng bốn giá trị trên. Không chạy trực tiếp file YAML.

Cấu hình TankBattleApplication riêng trong .idea/workspace.xml của máy hiện tại cũng đã được bổ sung
prod và working directory gốc; file này bị ignore nên các máy khác dùng .run. IDE đang mở có thể
giữ cấu hình cũ trong bộ nhớ: chọn cấu hình chia sẻ hoặc kiểm lại Edit Configurations trước khi Run.
Dừng lần chạy cũ rồi Run lại; thay biến trong terminal không tự cập nhật môi trường Run Configuration.
Nếu IDE đã đặt biến Render như JWT_PRIVATE_KEY=file:/etc/secrets/... thì giá trị IDE thắng .env;
bỏ cấu hình cũ hoặc đổi đúng đường dẫn trên máy.

Mở http://localhost:8080; thay cổng bằng PORT nếu bạn đặt cổng khác. AUTH_ISSUER phải giữ nhất quán
giữa các lần cấp/kiểm JWT của cùng hệ thống. `bootRun` kết nối DB đã chọn trong .env và chạy Flyway
khi khởi động; không dùng chính lệnh đó để chạy bộ kiểm thử trên Neon production.

## Kiểm chứng và tiếp tục

- ProductionProfileTest kiểm prod được chọn tường minh như IDE, import file thật, fallback Flyway URL, bind JWT/cookie HTTP, môi trường
  hosting ưu tiên và test profile không đọc .env; không khởi tạo kết nối DB.
- tools/test-production-startup.ps1 tạo .env trong thư mục smoke riêng, bỏ APP_ENV_FILE/DB/JWT khỏi env
  của tiến trình con rồi chạy JAR với working directory đó và profile prod tường minh. Chứng minh
  đường dẫn .env mặc định thực sự hoạt động, Flyway/schema, health và login/API trên
  PostgreSQL tạm. Fixture/keys/JVM được dọn trong finally; không đọc file local gốc hoặc truy cập Neon.
- Kết quả chạy thực tế mới nhất đọc [STATUS](STATUS.md), bàn giao [HANDOFF](HANDOFF.md).

Lượt kiểm lại đã chạy full build/Spotless 86 test Java, PostgreSQL riêng 39 test đều không lỗi;
JAR prod đọc .env mặc định tại working directory tạm, không APP_ENV_FILE hoặc DB/JWT trong env con,
đạt health/auth/admin/rooms/sáu migration. Fixture/keys/JVM/cluster đã dọn. Script dùng Remove-Item Env:
để xóa hẳn khóa khi kiểm đọc file; SetEnvironmentVariable($null) trên một số PowerShell/.NET có thể
giữ entry rỗng làm ghi đè giá trị file. Không mở ứng dụng bằng .env thật, không chạm Neon hoặc đổi key.

Tham khảo cơ chế import file không có phần mở rộng và thứ tự ưu tiên:
[Spring Boot external configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).
