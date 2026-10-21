# Auth: session, JWT và Google

Cập nhật 20/10/2026. Bộ test trước chuyển đổi đã chạy đạt bộ test Java, gồm luồng API auth thật với H2; Google thật còn chưa được kiểm chứng; PostgreSQL test đã có bằng chứng trong STATUS. Xem STATUS để phân biệt source với bằng chứng chạy.

## 1. Bố trí package

Mọi thành phần auth nằm trong `vn.edu.utc.tankbattle.auth`:

- `config`: hai SecurityFilterChain, cấu hình RSA/JWT, properties, post processor filter.
- `controller`: AuthApiController và SessionTokenController.
- `dto/request`, `dto/response`: model giao tiếp; không trả entity.
- `entity`, `repository`, `mapper`: tài khoản và truy cập dữ liệu.
- `service`: tài khoản local, cấp/rotate/revoke token, cookie, tài khoản Google/OIDC.
- `security`: AuthErrorMapper, AuthErrorHandler, SecurityErrorWriter.
- `exception`: AuthFailure và OidcAuthFailure nhận ValidateCode.
- `filter`, `model`: rate limit và projection nội bộ.

Catalog lỗi, i18n, envelope và validator thuộc common để nghiệp vụ phòng/trận dùng lại. Common không phụ thuộc service auth.

## 2. Hai loại phiên

| Kênh                     | Xác thực                        | Nơi giữ trạng thái              | CSRF                                                         |
| ------------------------ | ------------------------------- | ------------------------------- | ------------------------------------------------------------ |
| Trang Thymeleaf / Google | HttpSession và JSESSIONID       | Bộ nhớ server hiện tại          | Token session, form hoặc X-CSRF-TOKEN                        |
| /api/\*\*                | Bearer access JWT               | Chữ ký + auth_sessions trong DB | /api/auth/\*\* POST vẫn cần X-XSRF-TOKEN và cookie tương ứng |
| Refresh                  | Cookie TANK_REFRESH HttpOnly    | Chỉ hash refresh ở DB           | Bắt buộc                                                     |
| WebSocket                | Opaque ticket từ JWT hoặc session web | SocketHub/runtime          | Origin/membership/sequence và thu hồi đã triển khai, xem tài liệu 20 |

JWT không được tự suy thành phiên web. Session web không tự xác thực /api/\*\*. Sau form/Google login, POST /auth/token có CSRF session để nhận JWT nếu cần.

Logout API chỉ thu hồi auth_sessions, chưa hủy mọi HttpSession web. Session web hiện ở RAM nên server restart làm mất session; người chơi đăng nhập lại rồi khôi phục trận bằng userId đã lưu, không phải giữ nguyên JSESSIONID.

## 3. Đăng ký và đăng nhập local

1. GET /api/auth/csrf, giữ cookie XSRF-TOKEN và data.token.
2. POST /api/auth/register kèm X-XSRF-TOKEN; RequestNormalizationAdvice chuẩn hóa username/tên trước @Valid.
3. Validator chung kiểm tra username, tên tiếng Việt, mật khẩu và xác nhận. Mật khẩu giữ nguyên.
4. AccountService kiểm tra invariant và tạo user trong transaction. Unique username ở DB xử lý tranh chấp đăng ký.
5. POST /api/auth/login dùng AuthenticationManager; password hash PBKDF2 do PasswordEncoder quản lý.
6. TokenService tạo auth_session + refresh hash trong một transaction, ký JWT RS256.
7. Response trả access token và cookie refresh. Access token nên ở RAM phía client; không ghi vào URL hoặc log.

Sai tài khoản, sai mật khẩu, tài khoản bị vô hiệu hóa hoặc tài khoản chỉ dùng Google đều trả AUTH_002. DB không khả dụng trả SYS_006, không báo sai mật khẩu.

## 4. JWT

Access TTL mặc định 5 phút, refresh family tối đa 7 ngày, cấu hình trong tank.auth. JWT gồm iss, aud, sub=userId, sid, username, roles, token_kind=access, iat, exp và jti.

Decoder cố định RS256, kiểm tra chữ ký/issuer/thời gian, audience và claim bắt buộc, sau đó một lookup theo sid + userId để kiểm tra phiên còn hiệu lực và user enabled. Cơ chế này có đọc DB trên mỗi request API nhằm thu hồi access ngay từ request tiếp theo; chưa phải JWT hoàn toàn không trạng thái và chưa có kết quả đo tải.

Production cần cặp RSA PEM ổn định: private PKCS#8 và public X.509, truyền bằng JWT_PRIVATE_KEY/JWT_PUBLIC_KEY dạng Spring resource location, ví dụ file:/run/secrets/jwt-private.pem. Theo yêu cầu mới nhất của chủ đồ án, cặp key trong secrets/ được phép commit; không in nội dung key vào log/tài liệu. Docker vẫn loại key khỏi image và Render cần Secret Files. Profile test cho phép sinh key tạm; ứng dụng mặc định prod luôn yêu cầu PEM ổn định. Refresh còn hạn trong DB có thể cấp access mới theo key hiện tại.

## 5. Refresh và chống dùng lại

1. Refresh có dạng UUID token ID + dấu chấm + phần ngẫu nhiên 256 bit.
2. Tra token ID, khóa auth_session, đọc lại refresh sau khóa.
3. So sánh hash bằng MessageDigest.isEqual. Biết ID nhưng sai secret không được thu hồi phiên của người khác.
4. Token đã dùng: revoke cả session, ném AuthFailure(AUTH_REFRESH_REUSED).
5. Token hợp lệ: consume bản cũ, tạo bản mới trong cùng family; deadline family không kéo dài vô hạn.

Refresh dùng @Transactional(noRollbackFor = AuthFailure.class) để revoke khi replay vẫn commit dù trả lỗi. Đây là quy tắc có chủ đích; lỗi DB/encoder ngoài dự kiến vẫn rollback. Cần integration test PostgreSQL để chứng minh commit và tranh chấp thực tế.

Chính sách nghiêm ngặt: hai request dùng cùng refresh có thể làm cả family bị thu hồi. Frontend phối hợp refresh bằng Web Locks/BroadcastChannel trong api.js; trình duyệt thiếu Web Locks dùng session exchange. Luồng nhiều tab thật còn cần kiểm chứng, xem tài liệu 25.

## 6. Google

webSecurity chỉ gọi oauth2Login khi có ClientRegistrationRepository. Ứng dụng chưa dùng Google chỉ dùng prod; thêm Google bằng prod,google. Không thêm client giả để né lỗi startup. SecurityConfigTest kiểm cả hai nhánh; [tài liệu 22](22-github-render-cicd.md) hướng dẫn Render.

Chỉ bật khi profile google và GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET đã cấu hình:

1. Truy cập /oauth2/authorization/google.
2. Spring tạo authorization request và quản lý state; Google trả callback /login/oauth2/code/google.
3. Framework kiểm tra OIDC; GoogleOidcUserService yêu cầu email_verified.
4. Tìm account theo provider=GOOGLE + subject, không tự gộp bằng email.
5. Nếu chưa có, tạo user và external_identity trong transaction. Callback đồng thời được unique constraint bảo vệ, rollback rồi đọc lại.
6. Tạo session web; có thể gọi /auth/token như mục 2.

Khi tạo OAuth Client loại Web trong Google Cloud Console, khai báo đúng callback theo origin môi trường. Local dự kiến http://localhost:8080/login/oauth2/code/google; production thay bằng HTTPS domain thật. Không gửi secret qua chat.

Lỗi khởi tạo redirect và lỗi callback đều đi AuthErrorHandler.googleFailure. Chưa thử Google thật; không coi việc có annotation/config là đã đăng nhập thành công.

## 7. Lỗi custom ở cả filter và controller

Đọc [12 — mã lỗi/validation/i18n](12-ma-loi-validation-i18n.md).

- Controller: BusinessException/AuthFailure → ApiExceptionHandler → ApiResponseFactory.
- JWT thiếu/sai, form thất bại, Google thất bại, quyền và CSRF: AuthErrorHandler → SecurityErrorWriter → cùng factory.
- BearerTokenFilterPostProcessor gắn failure handler trực tiếp, bao gồm lỗi dịch vụ auth.
- GoogleRedirectFilterPostProcessor xử lý cả lỗi trước khi rời trang sang Google.
- Rate limit cũng dùng SecurityErrorWriter; không tự ghi text/JSON.
- ApiCsrfFilterPostProcessor giữ CSRF bắt buộc cho POST auth có Bearer, xử lý trường hợp Resource Server mặc định bỏ kiểm tra CSRF.

Không trả exception message, OAuth error_description, rejectedValue mật khẩu, SQL hoặc stack trace. Thông báo cấu hình lúc startup là chẩn đoán cho người vận hành, không phải response lỗi nghiệp vụ.

## 8. Kiểm thử

JUnit cho JSON filter, locale, lỗi Bearer do DB, Google redirect, MVC validation/login/malformed JSON và refresh đã chạy đạt. AuthSystemTest kiểm tra cả SecurityFilterChain thật, CSRF cho mọi POST auth, logout và hai request refresh đồng thời trên H2.

Chạy khi môi trường sẵn sàng:

```powershell
.\gradlew.bat test --tests "*AuthErrorHandlerTest" --tests "*AuthApiErrorContractTest" --tests "*TokenServiceErrorTest"
.\gradlew.bat clean build
```

Còn cần: kiểm thử JWT issuer/audience/expiry theo từng trường hợp âm, callback Google thật, cookie qua HTTPS và kiểm tra nhiều tab trong browser. Refresh/commit revoke đã có test trên PostgreSQL tạm. H2 không thay thế PostgreSQL integration.

## 9. Tham chiếu framework

Spring Security xử lý xác thực tại Servlet filter trước MVC; vì thế handler REST và handler filter phải cùng factory: [kiến trúc Servlet](https://docs.spring.io/spring-security/reference/servlet/architecture.html).

Hai điểm tùy biến được đối chiếu với source Spring Security 6.5: [BearerTokenAuthenticationFilter](https://github.com/spring-projects/spring-security/blob/6.5.11/oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource/web/authentication/BearerTokenAuthenticationFilter.java) và [OAuth2AuthorizationRequestRedirectFilter](https://github.com/spring-projects/spring-security/blob/6.5.11/oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client/web/OAuth2AuthorizationRequestRedirectFilter.java).
