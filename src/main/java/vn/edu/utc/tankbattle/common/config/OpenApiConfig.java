package vn.edu.utc.tankbattle.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import vn.edu.utc.tankbattle.common.constant.AppConstants;

/** Swagger lấy endpoint/schema từ code, không khai báo API dự kiến như đã tồn tại. */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "UTC Tank Battle — API học tập",
                        version = "1.0.0-dev",
                        description =
                                """
                Đọc /api/auth/csrf trước khi POST vào /api/auth/**.
                Header CSRF dùng X-XSRF-TOKEN, lấy từ data.token hoặc cookie XSRF-TOKEN.
                Login trả access token; bấm Authorize và điền token vào bearerAuth.
                Refresh token nằm trong cookie HttpOnly, không trả trong JSON.
                Refresh không gửi Authorization cũ đã hết hạn; dùng CSRF và cookie.
                Google dùng redirect OIDC, không phải API nhận Google ID token tự khai.
                Accept-Language: vi/en chọn ngôn ngữ thông báo, mặc định vi.
                Code/messageKey giữ nguyên; message và lỗi từng trường được dịch.
                WebSocket được mô tả riêng trong tài liệu luồng.
                Các endpoint đang phát triển, cần chạy kiểm thử trước khi nghiệm thu.
                """))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access JWT của ứng dụng; RS256, issuer/audience/expiry và kiểm tra phiên.")
@SecurityScheme(
        name = "csrfHeader",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-XSRF-TOKEN",
        description = "Lấy data.token từ GET /api/auth/csrf; cookie cùng origin tự gửi.")
@SecurityScheme(
        name = "webCsrfHeader",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-CSRF-TOKEN",
        description = "CSRF của session Thymeleaf từ meta/form, dùng riêng cho /auth/token.")
public class OpenApiConfig {
    private static final String LANGUAGE_PARAMETER = "acceptLanguage";
    private static final String LANGUAGE_REFERENCE =
            "#/components/parameters/" + LANGUAGE_PARAMETER;

    /**
     * Bổ sung tham số Accept-Language dùng chung vào mọi operation /api/** và /auth/token trong
     * Swagger. Tham số tham chiếu một component duy nhất, có lựa chọn vi/en và mặc định vi; kiểm
     * tra trước khi thêm để tránh khai báo trùng. Customizer chỉ mô tả hợp đồng API; LocaleConfig
     * chịu trách nhiệm đọc header khi request thực sự chạy.
     */
    @Bean
    OpenApiCustomizer languageHeader() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            openApi.getComponents()
                    .addParameters(
                            LANGUAGE_PARAMETER,
                            new HeaderParameter()
                                    .name(HttpHeaders.ACCEPT_LANGUAGE)
                                    .required(false)
                                    .description(
                                            "Ngôn ngữ thông báo: vi hoặc en. Bỏ trống/không hỗ trợ dùng vi; code/messageKey không thay đổi.")
                                    .schema(
                                            new StringSchema()
                                                    ._enum(AppConstants.SUPPORTED_LANGUAGES)
                                                    ._default(AppConstants.DEFAULT_LANGUAGE)));
            if (openApi.getPaths() == null) return;
            openApi.getPaths()
                    .forEach(
                            (path, item) -> {
                                if (path.startsWith("/api/") || "/auth/token".equals(path)) {
                                    for (Operation operation : item.readOperations()) {
                                        boolean declared =
                                                operation.getParameters() != null
                                                        && operation.getParameters().stream()
                                                                .anyMatch(
                                                                        parameter ->
                                                                                LANGUAGE_REFERENCE
                                                                                                .equals(
                                                                                                        parameter
                                                                                                                .get$ref())
                                                                                        || ("header"
                                                                                                        .equals(
                                                                                                                parameter
                                                                                                                        .getIn())
                                                                                                && HttpHeaders
                                                                                                        .ACCEPT_LANGUAGE
                                                                                                        .equalsIgnoreCase(
                                                                                                                parameter
                                                                                                                        .getName())));
                                        if (!declared)
                                            operation.addParametersItem(
                                                    new Parameter().$ref(LANGUAGE_REFERENCE));
                                    }
                                }
                            });
        };
    }

    /**
     * Diễn đạt điều kiện Bearer AND CSRF cho logout, logout-all và cấp vé socket. OpenAPI hiểu hai
     * requirement tách rời là OR, nên gộp cả hai scheme vào một requirement để Swagger phản ánh
     * đúng SecurityFilterChain. Springdoc gọi customizer khi tạo tài liệu; hàm không xác thực
     * request hay cấp quyền.
     */
    @Bean
    OpenApiCustomizer authSecurityRequirements() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path :
                    java.util.List.of(
                            "/api/auth/logout",
                            "/api/auth/logout-all",
                            "/api/auth/socket-ticket")) {
                PathItem item = openApi.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost()
                            .setSecurity(
                                    java.util.List.of(
                                            new io.swagger.v3.oas.models.security
                                                            .SecurityRequirement()
                                                    .addList("bearerAuth")
                                                    .addList("csrfHeader")));
                }
            }
        };
    }
}
