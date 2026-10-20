package vn.edu.utc.tankbattle.auth.service;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.exception.OidcAuthFailure;
import vn.edu.utc.tankbattle.auth.model.AuthenticatedOidcUser;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** OIDC chuẩn: framework kiểm tra state/nonce, issuer, audience và chữ ký Google. */
@Service
public class GoogleOidcUserService extends OidcUserService {
    private final GoogleAccountService accounts;

    /** Nhận nghiệp vụ ánh xạ identity Google sang tài khoản của hệ thống. */
    public GoogleOidcUserService(GoogleAccountService accounts) {
        this.accounts = accounts;
    }

    /**
     * Hoàn tất bước user-info sau khi framework kiểm chứng luồng OAuth/OIDC. Chỉ chấp nhận
     * registration google, email đã verified và tài khoản local enabled; resolve ánh xạ
     * provider/subject qua GoogleAccountService. Trả principal mang username/role nội bộ cho
     * session web. Lỗi DB được phân loại dịch vụ 503 thay vì báo sai thông tin Google.
     */
    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OidcAuthFailure(ValidateCode.AUTH_OAUTH_FAILED);
        }
        OidcUser principal = super.loadUser(request);
        if (!Boolean.TRUE.equals(principal.getEmailVerified())) {
            throw new OidcAuthFailure(ValidateCode.AUTH_OAUTH_FAILED);
        }
        try {
            UserEntity account = accounts.resolve(principal.getSubject(), principal.getFullName());
            if (!account.isEnabled()) {
                throw new OidcAuthFailure(ValidateCode.AUTH_OAUTH_FAILED);
            }
            return new AuthenticatedOidcUser(principal, account);
        } catch (DataAccessException unavailable) {
            throw new InternalAuthenticationServiceException(
                    ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey(), unavailable);
        }
    }
}
