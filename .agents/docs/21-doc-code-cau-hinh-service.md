# 21. Đọc cấu hình, service và luồng xử lý trong code

Tài liệu này dành cho thành viên mới đọc source. Javadoc tiếng Việt nằm ngay trên constructor/hàm; comment trong thân hàm giải thích những nhánh dễ sửa sai. Các hợp đồng API xem [tài liệu 11](11-swagger-va-api.md), transaction xem [tài liệu 13](13-transaction-va-concurrency.md), kịch bản kiểm thử xem [tài liệu 20](20-luong-backend-va-test.md).

## 1. Phân biệt những loại hàm đang có

| Loại                         | Ai gọi, vào lúc nào                                | Khi đọc cần chú ý                                                             |
| ---------------------------- | -------------------------------------------------- | ----------------------------------------------------------------------------- |
| Constructor                  | Spring tạo bean và truyền dependency lúc khởi động | Repository/service nào được dùng, cấu hình nào được bind                      |
| Hàm `@Bean`                  | Spring gọi để tạo đối tượng dùng chung             | Giá trị trả về được framework gọi về sau; không phải mỗi request lại tạo bean |
| Hàm service `@Transactional` | Controller hoặc bean khác gọi qua Spring proxy     | Phạm vi đọc/ghi DB, thứ tự khóa, exception nào làm rollback                   |
| Hàm `@Scheduled`             | Bộ lập lịch của Spring gọi theo chu kỳ             | Thread nào chạy, RAM nào cùng truy cập, có làm I/O trong tick không           |
| Callback/listener            | Security, WebSocket hoặc bộ phát sự kiện gọi       | Điều kiện kích hoạt, danh tính và thời điểm trước/sau commit                  |
| Helper `private`             | Một hàm trong cùng lớp gọi                         | Chỉ hỗ trợ bước nào; không tự có transaction riêng chỉ vì ở trong service     |
| Engine `static`              | Coordinator/test gọi bằng state và input           | Thuật toán thuần RAM; không xác thực HTTP hoặc ghi database                   |

`@Transactional(readOnly = true)` thể hiện giao dịch đọc và giúp framework tối ưu; không thay thế kiểm tra quyền hay ràng buộc DB. Annotation ghi trên phương thức có thể ghi đè mặc định đọc của lớp. Gọi hàm từ chính đối tượng bằng `this` không đi qua proxy nên không mở một giao dịch mới; caller bên ngoài bean mới là điểm cần lần theo.

Trong mỗi Javadoc nghiệp vụ, đọc lần lượt: hàm nhằm làm gì → điều kiện/caller → tác động RAM/DB → lỗi và cách khóa/ghi nếu có. Không cần suy luận mục đích chỉ từ tên hàm.

## 2. Cấu hình được dùng ở đâu

| File                                                                                                                                                                                               | Mục đích và liên hệ                                                                                                                                                                                                          |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [SecurityConfig](../../src/main/java/vn/edu/utc/tankbattle/auth/config/SecurityConfig.java)                                                                                                           | Tạo PasswordEncoder, UserDetailsService, AuthenticationManager và hai SecurityFilterChain. Chain API ưu tiên trước, dùng Bearer; chain web xử lý session/form/Google. Javadoc từng bean giải thích lý do tách và xử lý CSRF. |
| [JwtConfiguration](../../src/main/java/vn/edu/utc/tankbattle/auth/config/JwtConfiguration.java)                                                                                                       | Đọc cặp PEM hoặc tạo khóa tạm khi được phép; tạo encoder ký RS256 và decoder kiểm chữ ký/claims/phiên DB. Đọc lambda validator bên trong `jwtDecoder` để hiểu logout có hiệu lực dù JWT chưa hết hạn.                        |
| [AuthProperties](../../src/main/java/vn/edu/utc/tankbattle/auth/config/AuthProperties.java)                                                                                                           | Ý nghĩa từng thuộc tính `tank.auth`, đơn vị TTL, đường dẫn PEM, cờ cookie và kiểm tra cấu hình lúc startup.                                                                                                                  |
| [ApiCsrfFilterPostProcessor](../../src/main/java/vn/edu/utc/tankbattle/auth/config/ApiCsrfFilterPostProcessor.java)                                                                                   | Gắn matcher CSRF và handler lỗi chung lên filter thực do Spring tạo. Không bỏ CSRF cho thao tác auth dùng cookie.                                                                                                            |
| [BearerTokenFilterPostProcessor](../../src/main/java/vn/edu/utc/tankbattle/auth/config/BearerTokenFilterPostProcessor.java)                                                                           | Gắn handler vào filter Bearer, phân biệt token sai với lỗi DB thay vì trả tất cả thành lỗi đăng nhập.                                                                                                                        |
| [GoogleRedirectFilterPostProcessor](../../src/main/java/vn/edu/utc/tankbattle/auth/config/GoogleRedirectFilterPostProcessor.java)                                                                     | Đưa lỗi bắt đầu redirect OAuth vào cơ chế lỗi auth chung.                                                                                                                                                                    |
| [LocaleConfig](../../src/main/java/vn/edu/utc/tankbattle/common/config/LocaleConfig.java)                                                                                                             | Đọc Accept-Language và dùng chung MessageSource cho Bean Validation; mặc định vi, hỗ trợ vi/en.                                                                                                                              |
| [OpenApiConfig](../../src/main/java/vn/edu/utc/tankbattle/common/config/OpenApiConfig.java)                                                                                                           | Khai báo Bearer/CSRF, bổ sung header ngôn ngữ dùng chung cho mọi REST operation và ràng buộc bảo mật AND. Customizer mô tả Swagger; không phải bộ xử lý request thật.                                                        |
| [WebSocketConfig](../../src/main/java/vn/edu/utc/tankbattle/business/config/WebSocketConfig.java)                                                                                                     | Đăng ký handler và interceptor tại `/ws/game`. Luật xác thực nằm ở interceptor, xử lý message nằm ở handler.                                                                                                                 |
| [GameContentLoader](../../src/main/java/vn/edu/utc/tankbattle/business/config/GameContentLoader.java), [CampaignCatalog](../../src/main/java/vn/edu/utc/tankbattle/business/config/CampaignCatalog.java) | Đọc/kiểm tra JSON luật, địch/item, cửa/wave khi startup; lỗi cấu hình phải được phát hiện trước khi tạo trận.                                                                                                                |

Cấu hình giá trị nằm trong [application.yml](../../src/main/resources/application.yml) và file profile tương ứng. Không đưa TTL/đường dẫn khóa hoặc các ngưỡng vận hành rải rác vào controller.

## 3. Lần theo luồng tài khoản và token

Đọc [AccountService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/AccountService.java), [TokenService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/TokenService.java), [RefreshCookieService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/RefreshCookieService.java).

1. Đăng ký: controller nhận DTO đã normalize/validate → PasswordEncoder băm mật khẩu → `AccountService.create` kiểm tra lại invariant và ghi user. Entity có hash không được trả thẳng ra API.
2. Login: AuthenticationManager kiểm thông tin bằng UserDetailsService/PasswordEncoder → TokenService tạo phiên và `grant` → JWT ký bằng encoder, refresh thô đi vào cookie HttpOnly. DB chỉ giữ hash của refresh.
3. Refresh: `TokenService.refresh` tra token, khóa phiên rồi đọc lại trạng thái token → kiểm tra hash/hạn/consumed → tiêu thụ token cũ → `grant` tạo cặp mới trong cùng transaction.
4. Replay: phát hiện refresh đã dùng thì thu hồi phiên. `noRollbackFor = AuthFailure.class` ở riêng refresh giữ việc revoke được commit dù trả lỗi; nếu rollback mất revoke thì token bị dùng lại vẫn có thể còn hiệu lực.
5. Logout: service cập nhật revoke và phát sự kiện; listener đóng socket **sau commit**. Không đóng socket trước khi biết transaction có thành công hay không.

Google đi qua [GoogleOidcUserService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/GoogleOidcUserService.java) → [GoogleAccountService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/GoogleAccountService.java). Định danh liên kết là provider + subject do OIDC xác thực; không tự gộp tài khoản local chỉ vì email giống nhau. Javadoc `resolve` giải thích cách xử lý hai callback cùng tạo tài khoản.

[SocketTicketService](../../src/main/java/vn/edu/utc/tankbattle/auth/service/SocketTicketService.java) tạo vé ngẫu nhiên, ngắn hạn, dùng một lần cho handshake; không đưa access JWT vào URL. [BootstrapAdminInitializer](../../src/main/java/vn/edu/utc/tankbattle/auth/service/BootstrapAdminInitializer.java) INSERT admin sau Flyway khi chưa có, không reset mật khẩu. Profile và bộ seed demo local đã bỏ; ứng dụng mặc định prod/PostgreSQL.

## 4. Lần theo luồng phòng và bắt đầu trận

Đọc [RoomService](../../src/main/java/vn/edu/utc/tankbattle/business/service/RoomService.java), [GameCoordinator](../../src/main/java/vn/edu/utc/tankbattle/business/runtime/GameCoordinator.java), [MatchService](../../src/main/java/vn/edu/utc/tankbattle/business/service/MatchService.java).

1. `RoomService.create`: kiểm tra invariant, khóa guard tạo phòng rồi user, kiểm tra membership/giới hạn → tạo phòng và ghế chủ trong cùng transaction.
2. `RoomService.join`: khóa phòng rồi user → kiểm trạng thái/membership/sức chứa → cấp ghế trống và reset ready. Gửi lặp vào đúng phòng đang giữ không tạo thêm membership.
3. `ready`: chỉ đổi cờ của thành viên trong phòng WAITING; roster thay đổi phải reset ready để mọi người xác nhận lại.
4. `GameCoordinator.start`: kiểm tra quyền, roster/socket và lựa chọn Arena/Campaign → tạo GameState → gọi `MatchService.begin` kiểm tra lại điều kiện trong transaction và ghi match/players/checkpoint đầu → chỉ công bố runtime sau khi service trả về thành công.
5. `leave`: ghi bỏ cuộc bền nếu đang chơi, xóa membership, chuyển chủ hoặc đóng phòng trống. Coordinator cập nhật RAM sau bước DB; checkpoint cũ không được hồi sinh người đã rời.

Hàm đọc như `byCode` không mặc nhiên kiểm quyền thành viên; xem Javadoc/caller để biết nơi gọi `requireMember`. Không dùng một helper đọc chung như một API đã tự đủ phân quyền.

## 5. Input, tick và thuật toán trong RAM

Đọc [GameWebSocketHandler](../../src/main/java/vn/edu/utc/tankbattle/business/websocket/GameWebSocketHandler.java) → [GameCoordinator.input/tick](../../src/main/java/vn/edu/utc/tankbattle/business/runtime/GameCoordinator.java) → [GameEngine](../../src/main/java/vn/edu/utc/tankbattle/business/engine/GameEngine.java).

- Handler kiểm loại message, kích thước/tần suất và chuyển danh tính đã xác thực. Input chỉ mang ý định hướng/bắn, matchId và sequence.
- Coordinator loại input trùng/cũ, kiểm người chơi thuộc runtime rồi cập nhật map dưới khóa runtime. Tick dùng cùng khóa; handler không tự tính HP hoặc kết quả.
- `GameEngine.step` chạy thứ tự cố định: hết buff → input/di chuyển/bắn → AI/contact → đạn → nhặt item → xét kết thúc. Thời gian gameplay dùng tick; seed và bộ đếm nằm trong state để phục hồi.
- `moveBullets` xét cả đoạn bay, chọn va chạm gần nhất để đạn không xuyên tường/mục tiêu giữa hai tick. Damage được gom rồi áp một lần/mục tiêu; hai xe cùng chết được xét cùng tick.
- [CampaignEngine](../../src/main/java/vn/edu/utc/tankbattle/business/engine/CampaignEngine.java) xử lý AI/wave, điểm địch và item/buff; [CombatGeometry](../../src/main/java/vn/edu/utc/tankbattle/business/engine/CombatGeometry.java) chứa va chạm và PRNG.
- [GameRuntime](../../src/main/java/vn/edu/utc/tankbattle/business/runtime/GameRuntime.java) có comment phân biệt state checkpoint, input/presence tạm, cờ AtomicBoolean và trường volatile. `volatile` không thay khóa cho thao tác nhiều trường.
- [SocketHub](../../src/main/java/vn/edu/utc/tankbattle/business/websocket/SocketHub.java) quản lý kết nối/gửi frame, không quyết định luật thắng thua hoặc truy cập DB trong mỗi frame.

## 6. Checkpoint, phục hồi và kết toán

1. `queueCheckpoint`: chụp state dưới monitor runtime, dùng AtomicBoolean chặn xếp trùng rồi đưa sang executor. `MatchService.checkpoint` ghi trong transaction; tick không chờ DB.
2. Khi storage chậm quá ngưỡng, runtime sang SAVING để không tiếp tục tạo quá nhiều tiến trình chưa lưu. Callback thành công mới giải phóng trạng thái chờ phù hợp.
3. Tất cả người sống mất kết nối: pause trước, `queueRecovery` ghi checkpoint và deadline bền. Một người mất mạng trong khi người khác còn chơi mới áp grace/forfeit.
4. `recover` lúc startup đọc checkpoint qua [StateCodec](../../src/main/java/vn/edu/utc/tankbattle/business/engine/StateCodec.java), áp dấu bỏ cuộc từ DB, giữ matchId/nội dung luật/deadline. Mọi người còn sống phải kết nối và `resumeReady` mới tiếp tục.
5. Engine chỉ chuyển phase ENDING. `queueFinish` gọi `MatchService.finish` tính kết quả/batch ghi rồi mới phát FINISHED và bỏ runtime.
6. Terminal guard giữ FINISHED/ABORTED không bị checkpoint cũ ghi đè. Retry kết toán không tăng điểm nhiều lần; ranking tổng hợp từ kết quả đã lưu.

Đọc Javadoc của `submit`, `queueCheckpoint`, `queueRecovery`, `queueFinish`, `shutdown` để hiểu queue đầy, backoff, giữ cờ và dừng executor. Chú thích các hàm này giải thích cả luồng lỗi, không chỉ luồng thành công.

## 7. Bản lưu cá nhân và xếp hạng

[OfflineSaveService](../../src/main/java/vn/edu/utc/tankbattle/business/service/OfflineSaveService.java): `put` kiểm payload trước khi khóa user; revision trong request phải khớp trạng thái hiện có. Xóa giữ tombstone/revision để request cũ không ghi đè sau khi slot được tạo lại. List chỉ lấy metadata, GET slot mới tải state. Đây là đồng bộ cloud; phần lưu IndexedDB/chơi offline browser vẫn thuộc giai đoạn UI.

[RankingService](../../src/main/java/vn/edu/utc/tankbattle/business/service/RankingService.java): Arena lấy trận FINISHED, Campaign lấy best score từng cửa WIN theo độ khó. SQL aggregate/window nằm trong RankingRepository; hạng cá nhân được tính trên toàn bảng trước khi lọc user. Save client không đi vào luồng kết toán online.

## 8. Đọc luồng lỗi và ngôn ngữ

MVC: [ApiExceptionHandler](../../src/main/java/vn/edu/utc/tankbattle/common/exception/ApiExceptionHandler.java). Filter/handshake: [AuthErrorHandler](../../src/main/java/vn/edu/utc/tankbattle/auth/security/AuthErrorHandler.java), [AuthErrorMapper](../../src/main/java/vn/edu/utc/tankbattle/auth/security/AuthErrorMapper.java), [SecurityErrorWriter](../../src/main/java/vn/edu/utc/tankbattle/auth/security/SecurityErrorWriter.java). Cuối cùng đều dựng nội dung bằng [ApiResponseFactory](../../src/main/java/vn/edu/utc/tankbattle/common/i18n/ApiResponseFactory.java).

Caller truyền ValidateCode, không truyền câu lỗi. Factory lấy code/key/status và dịch message/lỗi trường theo Accept-Language. Bỏ header hoặc ngôn ngữ không hỗ trợ dùng vi; en-US về en, danh sách có q-value chọn ngôn ngữ được hỗ trợ. WebSocket chốt locale từ handshake bằng cùng resolver, giữ ngôn ngữ đó suốt kết nối. Swagger khai báo header chung trên từng operation để thử được bằng Try it out.

Khi thêm/sửa nghiệp vụ: cập nhật Javadoc sát hàm, comment ở nhánh có quyết định, hợp đồng API và test phù hợp. Không mô tả tính năng dự kiến như đã chạy; kết quả thực tế nằm trong [STATUS](STATUS.md).
