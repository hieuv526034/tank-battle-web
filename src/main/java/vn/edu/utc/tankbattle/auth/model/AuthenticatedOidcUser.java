package vn.edu.utc.tankbattle.auth.model;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;

/** Spring kiểm tra ID token; principal của ứng dụng vẫn dùng username nội bộ. */
public class AuthenticatedOidcUser extends DefaultOidcUser {
    private final String username;

    public AuthenticatedOidcUser(OidcUser googleUser, UserEntity user) {
        super(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                "sub");
        username = user.getUsername();
    }

    @Override
    public String getName() {
        return username;
    }
}
