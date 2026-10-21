# Hướng dẫn tiếp tục source UTC Tank Battle

## Đọc trước khi sửa

1. `README.md` và `.agents/README.md`: mục tiêu, cách chạy và mục lục.
2. `.agents/docs/STATUS.md`: trạng thái **thực tế**, lệnh kiểm tra đã/chưa chạy, trở ngại.
3. `.agents/docs/HANDOFF.md`: điểm tiếp tục, quyết định và thứ tự việc.
4. `.agents/docs/09-auth-jwt-google.md`, `.agents/docs/10-do-kho-cua-ai-item.md`, `.agents/docs/11-swagger-va-api.md`.
5. `.agents/docs/00-ke-hoach-tong-the.md` và `.agents/docs/07-backlog-va-nghiem-thu.md` là kế hoạch gốc; không mặc định TODO đã hoàn thành khi thấy có lớp tương ứng.
6. `.agents/docs/12-ma-loi-validation-i18n.md` và `.agents/docs/13-transaction-va-concurrency.md`: hợp đồng lỗi, validation, transaction và điểm cần kiểm chứng.
7. Tài liệu 14–16: Instant, H2/seed và catalog địch/item/điểm. Tài liệu 17 mô tả cấu trúc đã áp dụng; 18–20 ghi nghiệm thu backend, Neon production và luồng test.
8. [Tài liệu 21](docs/21-doc-code-cau-hinh-service.md): bản đồ đọc cấu hình/service/runtime; giữ Javadoc tiếng Việt sát từng hàm khi thay nghiệp vụ, không chỉ viết mô tả đầu lớp.

9. [Tài liệu 22](docs/22-github-render-cicd.md): GitHub CI, Render Free, env/Secret Files và giới hạn overlap khi phát hành. Người dùng tự push repo hieuv526034/tank-battle-web.
10. [Tài liệu 23](docs/23-gradle-va-thymeleaf.md): Gradle Wrapper, lệnh build/test, CI và phân biệt database kiểm thử với production.

## Yêu cầu người dùng phải giữ

- Nhóm có 4 người phát triển; số người chơi/phòng là yêu cầu riêng, đề xuất 2–4.
- Java 21, Spring Boot, Thymeleaf, PostgreSQL production; package `vn.edu.utc.tankbattle`.
- Code format đầy đủ, tên rõ; comment và tài liệu luồng bằng tiếng Việt.
- Tuyệt đối không dùng khai báo var trong Java, bao gồm source, test và ví dụ. Khai báo kiểu tường minh cho biến, vòng lặp và try-with-resources.
- Tách entity, DTO, request, response, mapper và repository; không nhét model vào service.
- Cấu trúc mục tiêu theo yêu cầu: auth riêng, common chung, business tổ chức theo tầng dùng chung entity/dto/controller/service/repository/mapper/model. Không chia lại mỗi nghiệp vụ room/match/ranking thành một bộ tầng riêng; xem tài liệu 17.
- Tái sử dụng base và `common.constant.AppConstants`; tránh base hình thức làm code khó hiểu.
- Không query/save repository theo từng phần tử. Dùng projection, query theo lô và batch khi phù hợp; engine chỉ dùng RAM.
- Game solo offline trong trình duyệt sau khi đã tải đủ, không bắt người chơi chạy Java.
- Hồ sơ tài khoản chỉ xem sau khi tạo; không mở lại đổi tên/username/role trong UI/API. PUT /api/profile cũ trả USER_007/403; không cập nhật dữ liệu tài khoản hiện có khi sửa source.
- Header/sidebar giữ vị trí, nội dung cuộn trong content-scroll; sidebar desktop thu gọn/mở rộng, mobile drawer. Bàn chơi dùng dialog nền tối, bản đồ 4:3 vừa viewport; không cho phím game điều khiển menu nền. Luồng mới ở tài liệu 29.
- Cả solo và online có lưu/chơi tiếp; online có phục hồi từ checkpoint sau server restart.
- Có lịch sử, xếp hạng, JWT/refresh, Google OIDC, độ khó, cửa ải và item buff.
- Có Swagger chi tiết: schema, ví dụ, điều kiện quyền/CSRF, lỗi và hướng dẫn thử.
- Lỗi nghiệp vụ và auth/security phải dùng AppConstants.ValidateCode, kèm key và bundle Việt/Anh. BusinessException/AuthFailure không nhận String; không trả exception.getMessage() ra client.
- Chuẩn hóa username/tên/mã phòng qua common util; mật khẩu giữ nguyên. Dùng validator chung cho request và invariant service.
- Mốc sự kiện dùng Instant. SQL giữ TIMESTAMP WITH TIME ZONE; chuyển kiểu driver tập trung tại JdbcTimeUtils.
- Mỗi thay đổi cập nhật tài liệu và kiểm thử thích hợp; không báo chạy đạt nếu chưa chạy.
- Quyết định mới nhất 20/10/2026: chủ đồ án cho phép commit .env/render.env, các biến môi trường demo và toàn bộ secrets/ gồm jwt-private.pem/jwt-public.pem. Không tự đổi lại .gitignore để chặn chúng; không in giá trị hoặc nội dung key vào tài liệu/log. Docker vẫn loại env/key khỏi image, Render dùng Secret Files; dữ liệu H2/build/cache vẫn bị Git bỏ qua.

## Kiến trúc hiện chọn

- Spring Boot 3.5.16; Gradle Wrapper 8.14.6 (Groovy DSL); ứng dụng chỉ có prod/PostgreSQL, mặc định prod. Đã bỏ application-local.yml và seed demo local theo yêu cầu mới nhất. H2 chỉ testRuntimeOnly; Flyway quản lý schema. Không đưa H2/local hoặc cờ -Pproduction trở lại runtime.
- JPA cho CRUD entity; JDBC cho projection/khóa/batch. JDBC write cần cập nhật audit tương ứng và tránh đọc entity cũ trong cùng persistence context.
- API nghiệp vụ riêng dùng Bearer JWT; GET /api/rankings và GET /api/rankings/campaign công khai theo yêu cầu P0, các route hạng/tiến độ cá nhân vẫn cần JWT. Trang Thymeleaf dùng session/form/OIDC. Không đưa JWT vào query string.
- Refresh trong cookie HttpOnly; endpoint dùng cookie có CSRF; không tắt CSRF toàn bộ.
- Google nhận diện bằng `provider + subject`; không tự nối tài khoản local theo email.
- WebSocket trực tiếp là lựa chọn implementation hiện tại, thay STOMP trong đề xuất ban đầu; handler/handshake đã triển khai, xem tài liệu 20 và STATUS.
- Engine/checkpoint phải đủ state, có phiên bản và có kiểm thử restore. Không scale nhiều instance khi chưa có ownership runtime.
- Ranking Arena/Campaign chỉ từ online FINISHED; giữ matchId khi khôi phục; offline không cộng bảng online.

## Quy tắc bàn giao mỗi lần dừng

- Cập nhật STATUS: file đã đổi, điều đã thử, điều chưa thử, lỗi còn lại.
- Cập nhật HANDOFF: bước tiếp theo và những ràng buộc cần nhớ.
- Cập nhật API doc khi route/request/response/quyền thay đổi.
- Không đưa `.env`, token, key, cookie hoặc Google secret vào tài liệu/log.
- Không đánh dấu đồ án hoàn tất khi chỉ có backend skeleton hoặc chưa kiểm tra UI/runtime.

## Source backend và nơi lưu tài liệu

- Toàn bộ hướng dẫn đầy đủ, trạng thái và tài liệu luồng nằm trong .agents; AGENTS.md ở gốc chỉ trỏ tới đây để AI tự tìm thấy khi clone sang nơi khác.
- Backend chỉ cần JDK 21/Gradle; không tạo lại package.json, Node tests hoặc Prettier. Kiểm tra hợp đồng source bằng SourceContractTest trong src/test/java.
- tools chứa script đang dùng cho PostgreSQL/CI/Render; dữ liệu sinh ra đặt dưới build, không tạo .tools.
- Bootstrap admin chạy sau Flyway V6 với guard DB; không đổi mật khẩu hoặc tự nâng quyền tài khoản trùng username. BOOTSTRAP_ADMIN_PASSWORD bắt buộc khi tạo lần đầu; không có mật khẩu mặc định cho ứng dụng. Test có cấu hình/DB riêng trong src/test.
- Database production neondb, schema tank_battle: Flyway/history, Hibernate default_schema và Hikari schema phải thống nhất tank_battle. App/Flyway hiện dùng direct endpoint + Hikari pool 5; không dựa vào SET search_path của Neon pooler. Test không dùng Neon.
- Giữ riêng quyền ADMIN trên /api/admin/**; cấu hình gameplay hiện ở JSON, chưa có màn hình/API CRUD quản trị catalog.
- Prod tự import optional:file:${APP_ENV_FILE:./.env}[.properties] tại working directory; IDE dùng cấu hình chia sẻ .run/TankBattleApplication-prod-local.run.xml (prod, $PROJECT_DIR$). Không để IDE tự đoán working directory vì optional import có thể bỏ qua .env rồi lỗi Flyway. Biến môi trường thật ưu tiên cao hơn. Không đưa import .env vào profile test; ProductionProfileTest dùng fixture. .env local có JWT file:./secrets/... và cờ cookie HTTP false, Render vẫn mặc định HTTPS true và không đóng gói .env. Smoke JAR dùng .env mặc định trong working directory tạm, không APP_ENV_FILE hoặc DB/JWT trong env con. Xem tài liệu 30; không dùng Neon để kiểm thử startup.
