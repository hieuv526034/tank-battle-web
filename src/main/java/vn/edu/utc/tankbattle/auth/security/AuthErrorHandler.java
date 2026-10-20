package vn.edu.utc.tankbattle.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** Một nơi xử lý lỗi của filter JWT, form, Google và phân quyền/CSRF. */
@Component
public class AuthErrorHandler {
    private final RequestMatcher apiEndpoints =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    private final SecurityErrorWriter errors;

    /** Nhận writer chung để mọi nhánh lỗi Spring Security dùng cùng mã/key/locale. */
    public AuthErrorHandler(SecurityErrorWriter errors) {
        this.errors = errors;
    }

    /**
     * Xử lý request chưa có danh tính: thiếu Authorization trả AUTH_LOGIN_REQUIRED, có
     * Authorization thì chuyển nhánh lỗi token. API được thêm WWW-Authenticate Bearer để client
     * biết cơ chế xác thực cần dùng.
     */
    public void unauthenticated(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            tokenFailure(request, response, failure);
            return;
        }
        if (apiEndpoints.matches(request)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        errors.write(request, response, ValidateCode.AUTH_LOGIN_REQUIRED);
    }

    /**
     * Map lỗi Bearer sang AUTH_INVALID_TOKEN hoặc SYS_SERVICE_UNAVAILABLE nếu hạ tầng lỗi.
     * Challenge invalid_token chỉ chứa thông tin chuẩn, không đưa cause/exception message vào HTTP
     * header.
     */
    public void tokenFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException {
        ValidateCode code = AuthErrorMapper.tokenFailure(failure);
        if (code == ValidateCode.AUTH_INVALID_TOKEN) {
            // Challenge chuẩn; không đưa mô tả/cause của thư viện vào header.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        }
        errors.write(request, response, code);
    }

    /**
     * Chuyển lỗi form/password login qua AuthErrorMapper rồi ghi JSON custom. Lỗi hệ thống phân
     * biệt với sai thông tin để người dùng không hiểu nhầm cần đổi mật khẩu.
     */
    public void loginFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException {
        errors.write(request, response, AuthErrorMapper.loginFailure(failure));
    }

    /** Xử lý lỗi tạo redirect hoặc callback Google theo cùng hợp đồng AUTH_OAUTH_FAILED/503. */
    public void googleFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException {
        errors.write(request, response, AuthErrorMapper.googleFailure(failure));
    }

    /**
     * Phân biệt CsrfException thành AUTH_CSRF_INVALID với lỗi quyền thành AUTH_ACCESS_DENIED. Cả
     * hai dùng HTTP 403 và được dịch bằng locale của request.
     */
    public void denied(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException {
        errors.write(
                request,
                response,
                failure instanceof CsrfException
                        ? ValidateCode.AUTH_CSRF_INVALID
                        : ValidateCode.AUTH_ACCESS_DENIED);
    }
}
