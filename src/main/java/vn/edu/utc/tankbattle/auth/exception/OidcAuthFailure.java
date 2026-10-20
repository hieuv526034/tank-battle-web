package vn.edu.utc.tankbattle.auth.exception;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** Lỗi OIDC nội bộ cũng khai báo bằng ValidateCode; failure handler dịch khi trả HTTP. */
public class OidcAuthFailure extends OAuth2AuthenticationException {
    public OidcAuthFailure(ValidateCode code) {
        super(new OAuth2Error(code.getCode(), code.getMessageKey(), null));
    }
}
