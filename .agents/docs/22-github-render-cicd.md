# 22. Đưa source lên GitHub và deploy Render Free với Neon

Dành cho repo [hieuv526034/tank-battle-web](https://github.com/hieuv526034/tank-battle-web). Người dùng tự push source và thao tác Dashboard. Source đã có backend Spring, UI Thymeleaf/Canvas và solo offline; xem [29](29-sua-loi-va-bo-cuc-ban-choi.md) về nghiệm thu và giới hạn. Render chạy Java, Neon giữ dữ liệu PostgreSQL production.

## 1. Các file đã chuẩn bị

| File                                                    | Dùng để làm gì                                                                                                    |
| ------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| [Dockerfile](../../Dockerfile)                             | Build JAR bằng JDK 21/Gradle rồi chạy bằng JRE 21 với user không phải root. Build không gọi Spring/Flyway hay DB. |
| [.dockerignore](../../.dockerignore)                       | Chỉ cho Gradle Wrapper/build script/src vào build context, loại key, .env và dữ liệu local.                       |
| [render.yaml](../../render.yaml)                           | Blueprint cấu hình Free/Ohio/Docker/health/env. Có thể dùng làm bản đối chiếu khi điền form thủ công.             |
| [backend-ci.yml](../../.github/workflows/backend-ci.yml)   | Mọi push/PR tự test H2/PostgreSQL, build JAR/Docker và smoke test container bằng prod profile với DB test.        |
| [GenerateJwtKeys.java](../../tools/GenerateJwtKeys.java)   | Sinh cặp PEM ổn định; không ghi đè file đang có, không in nội dung key.                                           |
| [ci-container-smoke.sh](../../tools/ci-container-smoke.sh) | Test container trên Linux CI với PostgreSQL mới và key dùng một lần.                                              |

Ứng dụng được cấu hình nghe 0.0.0.0 và PORT, phù hợp [yêu cầu web service của Render](https://render.com/docs/web-services). Không chọn Static Site cho backend Java.

## 2. Push source và bật GitHub Actions

Trong thư mục project, nếu chưa có Git:

```powershell
git init -b main
git remote add origin https://github.com/hieuv526034/tank-battle-web.git
git check-ignore .data/tankbattle.mv.db
.\gradlew.bat spotlessApply
git add .
git status --short
git commit -m "Prepare backend and Render deployment"
git push -u origin main
```

Nếu repo đã có Git/remote thì bỏ hai lệnh init/add origin. Nếu remote có lịch sử, clone repo về rồi chép source vào hoặc đồng bộ lịch sử trước; không force push để bỏ code của thành viên khác.

Theo quyết định mới nhất của chủ đồ án, `.env`, `render.env` và toàn bộ `secrets/` gồm hai file JWT PEM được phép xuất hiện trong status và commit để nhóm dùng chung cấu hình demo. `.gitignore` cho phép secrets/**, còn PEM/key ngoài thư mục này vẫn bị bỏ qua. Không commit .data, .tools, target, build, .gradle hoặc node_modules. Docker vẫn loại env/key khỏi image; commit key không tự tạo Secret Files trên Render. Cần commit gradlew, gradlew.bat và gradle/wrapper, gồm cả gradle-wrapper.jar. CI vẫn dùng DB/key test riêng, không nạp .env hoặc render.env vào job test.

Mở tab **Actions → Backend checks**. Mọi push và PR đều tự chạy; không cần mỗi thành viên tự tạo workflow. Job thứ nhất kiểm tra source, bộ kiểm thử Java hiện tại và hai lớp auth/business trên PostgreSQL riêng. Job thứ hai build Dockerfile và chạy container prod profile với PostgreSQL test mới, kiểm health/auth/migration. Có artifact JAR và báo cáo test để tải.

Khuyến nghị Settings → Rules/Rulesets bảo vệ main: thay đổi qua PR và yêu cầu cả hai job đạt trước merge. CI chỉ cần quyền contents:read, không cần mật khẩu Neon/Render.

### Vì sao workflow có tankbattle_test?

GitHub Actions tạo PostgreSQL service riêng trong runner để test có thể insert, cập nhật và xóa dữ liệu mà không tác động người chơi. Database `tankbattle_test` phục vụ job verify; `tankbattle_smoke` phục vụ job chạy thử container. Cả hai dùng mật khẩu kiểm thử công khai `ci_test_only` và bị xóa cùng runner khi job kết thúc.

Đây không phải cấu hình kết nối của Render. Render lấy `SPRING_DATASOURCE_*` và `SPRING_FLYWAY_URL` từ Environment Variables để kết nối **Neon production**. Luồng là: commit → GitHub kiểm thử DB tạm → CI đạt → Render build Docker → ứng dụng chạy prod và kết nối Neon. Không thay URL test trong workflow bằng URL Neon.

Mỗi lần chạy, Flyway tạo schema trên DB test mới để kiểm migration. Khi Render chạy, Flyway chỉ áp dụng migration chưa chạy trên Neon; không tạo lại hoặc xóa dữ liệu production. Migration mới vẫn cần được xem xét và backup trước phát hành.

## 3. Tạo Web Service trên Render

1. Đăng nhập Render bằng GitHub hoặc kết nối GitHub trong Render.
2. **New → Web Service → Git Provider**; cấp quyền repo và chọn tank-battle-web. Chọn qua Git Provider để Render nhận sự kiện/check CI, không chỉ dán URL public.
3. Điền form:

| Ô                              | Giá trị                             |
| ------------------------------ | ----------------------------------- |
| Name                           | tank-battle-web                     |
| Branch                         | main                                |
| Language / Runtime environment | **Docker**                          |
| Region                         | **Ohio**, gần vùng Neon hiện dùng   |
| Root Directory                 | Để trống, project nằm ngay gốc repo |
| Instance Type / Plan           | **Free**                            |

Không cần cài Java/PostgreSQL trên máy chủ Render bằng tay. Dockerfile tự build; Neon là DB ngoài, không tạo thêm database Render.

Docker đóng gói bằng `bootJar` (mọi build dùng PostgreSQL, không còn cờ -Pproduction), không chạy test hoặc tạo DB lúc build; không gửi src/test vào Docker context. JAR production không có H2/application-local.yml và runtime mặc định profile prod. GitHub vẫn kiểm thử trên runner/DB tạm trước deploy. Gradle thuộc build stage, không chạy chung JVM ứng dụng. Xem [chi tiết Gradle](23-gradle-va-thymeleaf.md).

## 4. Environment Variables: điền từng dòng

Phiên bản mới bổ sung bootstrap admin: nhập `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_USERNAME=admin`, `BOOTSTRAP_ADMIN_PASSWORD` lấy từ render.env (12–64 ký tự). Đã bổ sung vào file môi trường hiện tại; cần import lại trên Render trước deploy. Lần khởi động sau giữ nguyên mật khẩu trong DB, không reset theo env. Xem [luồng admin](24-bootstrap-admin-va-phan-quyen.md).

Đã có **render.env**, lấy thông tin Neon từ .env và dùng đúng tên biến trong source. File chứa cấu hình kết nối thật, được phép commit theo yêu cầu chủ đồ án; Docker vẫn loại trừ. AUTH_ISSUER là https://tank-battle-web.onrender.com; dùng **Environment → Add from .env** để import. Commit file không tự cập nhật Environment Variables trên Render.

Bản công khai để chuyển máy là [render.env.example](../../render.env.example). Máy khác đã có .env nhưng chưa có render.env có thể chạy:

```powershell
.\tools\Export-RenderEnvironment.ps1 -PublicUrl https://tank-battle-web.onrender.com
```

Script không in secret và từ chối ghi đè file đang có. Nếu đã đổi mật khẩu Neon, tự cập nhật mật khẩu mới vào file trước khi import.

Nhấn Add Environment Variable. Giá trị không bọc dấu nháy trong Dashboard.

| Key                        | Value                                                |
| -------------------------- | ---------------------------------------------------- |
| SPRING_PROFILES_ACTIVE     | prod                                                 |
| PORT                       | 10000                                                |
| SPRING_DATASOURCE_URL      | JDBC direct URL Neon/neondb/schema tank_battle       |
| SPRING_DATASOURCE_USERNAME | Role/user trong trang Connect của Neon               |
| SPRING_DATASOURCE_PASSWORD | Mật khẩu role Neon đã đổi, chỉ dán vào ô secret      |
| SPRING_FLYWAY_URL          | JDBC **direct** URL tới cùng database, theo mẫu dưới |
| AUTH_ISSUER                | https://tank-battle-web.onrender.com                 |
| JWT_PRIVATE_KEY            | file:/etc/secrets/jwt-private.pem                    |
| JWT_PUBLIC_KEY             | file:/etc/secrets/jwt-public.pem                     |
| JWT_KEY_ID                 | tank-v1                                              |
| DB_POOL_SIZE               | 5                                                    |
| API_DOCS_ENABLED           | true cho demo Swagger; false nếu muốn tắt            |


Lấy hostname thật từ **Neon → Connect**. Hai URL có cấu trúc:

```text
SPRING_DATASOURCE_URL:
jdbc:postgresql://YOUR_NEON_DIRECT_HOST/neondb?sslmode=require&channelBinding=require&currentSchema=tank_battle

SPRING_FLYWAY_URL:
jdbc:postgresql://YOUR_NEON_DIRECT_HOST/neondb?sslmode=require&channelBinding=require&currentSchema=tank_battle
```

Trong Connect, **tắt connection pooling** và chọn database neondb để lấy direct host cho cả datasource/Flyway. Hikari giới hạn 5 kết nối; schema tank_battle phải khớp Flyway/JPA/JDBC. Cấu hình đã đổi theo yêu cầu schema mới: import lại render.env trên Render. JDBC URL bắt đầu jdbc:postgresql://, không nhét user:password@ vào URL. Tham số Java là channelBinding, khác channel_binding trong URI ban đầu. Xem [lý do dùng direct](19-neon-production.md).

.env có datasource JDBC và thông tin DB; render.env bổ sung profile/port/issuer/key path phù hợp Render. Nhóm được commit hai file theo yêu cầu chủ đồ án; không sao chép giá trị vào tài liệu hoặc log. Nếu đổi thông tin Neon, cập nhật cả hai file và Environment trên Render.

AUTH_ISSUER hiện là https://tank-battle-web.onrender.com, không có dấu / cuối. Nếu đổi domain, cập nhật đồng thời Render Environment và file cấu hình tương ứng.

## 5. Advanced → Secret Files

Cặp key đã được tạo tại secrets/jwt-private.pem và secrets/jwt-public.pem, được phép commit theo yêu cầu chủ đồ án. Sau clone dùng lại đúng cặp đã có; chỉ khi chưa có key mới chạy bằng JDK 21:

```powershell
java tools/GenerateJwtKeys.java secrets
```

Tạo hai mục Secret Files:

| Filename trên Render | Contents                                                            |
| -------------------- | ------------------------------------------------------------------- |
| jwt-private.pem      | Toàn bộ nội dung secrets/jwt-private.pem, gồm BEGIN/END PRIVATE KEY |
| jwt-public.pem       | Toàn bộ nội dung secrets/jwt-public.pem, gồm BEGIN/END PUBLIC KEY   |

Giữ xuống dòng PEM thật. Không dán đường dẫn Windows vào Contents. Render mount file tại /etc/secrets; Docker user thuộc group 1000 để đọc được theo [hướng dẫn secret Docker](https://render.com/docs/docker-secrets).

Giữ nguyên cặp key qua các lần deploy. Khóa local tạm không dùng cho prod; không sinh khóa mới trong Docker ENTRYPOINT. Giữ bản backup riêng để chuyển máy còn tiếp tục được.

Nếu dùng **New → Blueprint** thay form Web Service, render.yaml sẽ hỏi các biến sync:false. Secret Files vẫn cần thêm vào Environment của service; nếu lần đầu thiếu key thì thêm đủ hai file rồi Manual Deploy lại.

## 6. Advanced: các ô còn lại

| Ô                              | Điền                                                     |
| ------------------------------ | -------------------------------------------------------- |
| Health Check Path              | /actuator/health/readiness                               |
| Registry Credential            | Để trống; base image public, không dùng private registry |
| Docker Build Context Directory | .                                                        |
| Dockerfile Path                | ./Dockerfile                                             |
| Docker Command                 | Để trống; dùng ENTRYPOINT đã có                          |
| Pre-Deploy Command             | Để trống; Flyway chạy khi Spring khởi động               |
| Auto-Deploy                    | **After CI Checks Pass**, theo nhánh main                |

Không nhập Gradle build command vào Docker Command; cũng không dùng java -jar build/libs/... vì image chỉ chứa /app/app.jar. Không đặt migration vào Docker build.

Sau khi GitHub Actions xanh và điền đủ env/key, bấm **Create Web Service** để triển khai lần đầu. Chưa cần gọi API Render hoặc thêm deploy hook vào GitHub.

## 7. Database tự tạo bảng như thế nào

Docker build chỉ đóng gói source → Render start JAR bằng profile prod → Flyway dùng direct URL chạy V1–V6 → Hibernate validate entity/schema → security/runtime khởi động → health/readiness UP.

Migration tạo bảng/index/ràng buộc và dữ liệu khóa hệ thống cần thiết. Entity khai báo @Index/@UniqueConstraint tương ứng với các ràng buộc đã có; Hibernate ddl-auto=validate không tạo DDL từ annotation. Không cần bật create/update hoặc sửa migration đã phát hành để chạy lại. Không tự thêm tài khoản demo production hoặc dữ liệu test. Người dùng thật đăng ký qua API. Catalog luật/địch/item/cửa là JSON được load khi startup, không phải mọi dữ liệu mặc định đều nằm trong SQL.

Flyway tạo tank_battle nếu chưa có, giữ lịch sử tại tank_battle.flyway_schema_history; JPA/JDBC cùng schema. Lần sau chỉ chạy migration mới, không xóa/tạo lại toàn bộ. Giữ nguyên V1–V6; thêm V7 trở đi khi đổi DDL. Có thể dùng SQL Editor Neon đọc lịch sử:

```sql
SELECT version, description, success
FROM tank_battle.flyway_schema_history
ORDER BY installed_rank;
```

Data tài khoản, phòng, checkpoint, kết quả và save cloud nằm trên Neon; redeploy Render không xóa DB. Không dùng H2 file trên Render Free vì filesystem không bền. [Giới hạn Free](https://render.com/docs/free) gồm ngủ khi không có traffic khoảng 15 phút và khởi động lại có thể chậm; chuẩn bị mở health trước buổi demo.

## 8. CI/CD và thời điểm phát hành

Luồng đã cấu hình: push/merge main → GitHub Actions test/source/PostgreSQL → Docker build + startup test → **Ready for Render** → Render build/deploy. Check cuối chỉ đạt khi cả verify và docker đều success; PR và nhánh khác vẫn được CI kiểm tra, Render chỉ phát hành nhánh main.

Trên Render, mở **tank-battle-web → Settings → Build & Deploy**:

1. Repository phải liên kết GitHub qua Git Provider: hieuv526034/tank-battle-web.
2. Branch: main.
3. Auto-Deploy: **After CI Checks Pass** → Save.

Nếu tạo service thủ công thì sửa render.yaml trên Git chưa tự đổi Settings; phải thực hiện bước trên. Blueprint dùng autoDeployTrigger: checksPass. Không cần Render API token/deploy hook trong GitHub. [Cơ chế Render chờ CI](https://render.com/docs/deploys).

Env và Secret Files vẫn nằm trên Render; CI chỉ dùng DB/key test. Không nối preview service vào Neon production. Khi chưa thấy deploy, kiểm Actions của commit main có đủ ba check xanh và tab Events của Render.

**Quy trình demo:** chỉ merge/push main khi không còn trận RUNNING/RECOVERING và ngừng thao tác đến khi deploy hoàn tất. Render có thể chạy hai phiên bản trong lúc đổi bản; engine hiện chưa có ownership/fencing xuyên process, nên chưa hỗ trợ rolling deploy an toàn khi đang chơi. Nếu cần kiểm soát thời điểm, đổi Auto-Deploy về Off rồi phát hành thủ công.

## 9. Kiểm tra sau deploy và xử lý lỗi

- Mở https://tank-battle-web.onrender.com/actuator/health/readiness, cần HTTP 200 và status UP.
- API_DOCS_ENABLED=true: mở /swagger-ui.html. Trang gốc phải mở vỏ game Thymeleaf; nếu 404, kiểm commit/image đang chạy và log routing.
- GET /api/auth/me chưa có JWT phải trả 401/AUTH_001; Accept-Language: en trả message tiếng Anh.
- Thử CSRF → register/login → Bearer → tạo phòng. Chỉ tạo tài khoản demo do nhóm chủ động cần dùng.
- WebSocket public dùng wss://tank-battle-web.onrender.com/ws/game?ticket=..., cùng origin HTTPS. Không chuyển về ws:// ở public.
- Theo dõi Logs/Events: build thành công khác với service healthy.

| Lỗi                                          | Cần kiểm tra                                                                                    |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Thiếu ClientRegistrationRepository           | Source đã sửa để chỉ gọi oauth2Login khi có registration; chưa dùng Google thì chỉ profile prod |
| Phải cấu hình JWT_PRIVATE_KEY/JWT_PUBLIC_KEY | Đúng tên env và đủ hai Secret Files, nội dung PEM hợp lệ                                        |
| FileNotFound/permission PEM                  | Đường dẫn file:/etc/secrets/... và Docker chạy uid/gid 1000                                     |
| Không tìm thấy driver/URL                    | Dùng jdbc:postgresql:// và đúng biến SPRING_DATASOURCE_URL                                      |
| Authentication failed DB                     | Đúng role/password hiện tại của Neon; không tự thêm nháy vào Dashboard                          |
| Flyway lỗi/checksum                          | Kiểm direct URL, cùng DB/schema, migration mới; không repair/clean/drop tùy tiện                |
| BOOTSTRAP_ADMIN_PASSWORD cần 12–64 ký tự       | Thêm/cập nhật đúng biến trong Environment của Render, dùng giá trị hợp lệ ở render.env rồi Save/redeploy |
| No open ports/health fail                    | PORT=10000, Docker Command trống, profile prod, xem log startup                                 |
| Trả 404 ở /                                  | Source đã có trang chủ; kiểm commit/image mới, routing và log, đồng thời kiểm readiness          |

Log có Flyway version 6 và Hibernate validate thành công nhưng `relation "rooms" does not exist` hoặc `relation "app_locks" does not exist`: kiểm schema JDBC trước. Source đặt `spring.datasource.hikari.schema=tank_battle` cho mọi connection, cùng schema Flyway/JPA. Push/redeploy đúng commit đã sửa; import lại render.env nếu Dashboard còn cấu hình cũ. Nếu Dashboard có `SPRING_DATASOURCE_HIKARI_SCHEMA` ghi đè thì đặt `tank_battle`. Giữ direct endpoint và hai RSA Secret Files. Không dùng repair/clean/drop/baseline để chữa sai search_path.

Cảnh báo Flyway chỉ hỗ trợ PostgreSQL tối đa 17 xuất phát từ thư viện Flyway cũ; source pin cả hai module 11.20.3 hỗ trợ PostgreSQL 18. Đây không phải cảnh báo cú pháp SQL trong migration bị cũ. Smoke mới kiểm thêm CSRF/login admin/API quản trị/API phòng để phát hiện lệch JDBC schema trước deploy. Docker build vẫn chỉ đóng gói, không chạy test hoặc kết nối DB.

Google bật sau: tạo OAuth Client Web, callback https://tank-battle-web.onrender.com/login/oauth2/code/google, thêm GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET và đổi profile thành prod,google. Chưa có hai giá trị thì không bật profile google.

Bằng chứng đã/chưa chạy nằm trong [STATUS](STATUS.md); tài liệu này không khẳng định đã deploy thành công trên tài khoản Render của bạn.

## Tăng tốc GitHub CI

- Hai job verify và docker chạy song song trên runner/DB tạm riêng. deploy-ready vẫn chờ cả hai và chỉ thành công khi cả hai success; Render tiếp tục After CI Checks Pass.
- verify dùng gradle/actions/setup-gradle@v5 để cache Wrapper/dependency/build state, bỏ cache: gradle ở setup-java để không có hai cơ chế cache cùng Gradle User Home.
- Gọi build postgresTest trong cùng một lần Gradle, bỏ clean trên runner mới. Task test lọc TEST_DATABASE_* khỏi môi trường JVM con nên vẫn dùng H2 riêng; postgresTest giữ nguyên env PostgreSQL CI và luôn chạy lại, không dùng cache kết quả DB.
- Khai báo source/docs mà SourceContractTest đọc thành task inputs để cache không bỏ sót thay đổi hợp đồng source.
- Docker dùng setup-buildx-action@v4/build-push-action@v7, load:true để chạy smoke ngay tại runner, cache-from/cache-to type=gha,mode=max. Cache export có ignore-error=true vì lỗi lưu cache không làm hỏng kết quả image đã build. Không push image lên registry, không thêm secret production.
- Cache GitHub không chia sẻ trực tiếp sang Render. Render vẫn tự build Dockerfile và có cache riêng. Lần đầu phải tạo cache; chưa có số đo thời gian trước/sau trên GitHub.

Tham chiếu: [Gradle caching](https://github.com/gradle/actions/blob/v5/docs/setup-gradle.md), [Docker GitHub cache](https://docs.docker.com/build/ci/github-actions/cache/).
