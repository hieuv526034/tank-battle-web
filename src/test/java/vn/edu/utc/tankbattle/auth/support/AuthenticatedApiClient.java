package vn.edu.utc.tankbattle.auth.support;

import jakarta.servlet.http.Cookie;

/** Fixture test: chỉ dùng trong test, không ghi token/mật khẩu ra log. */
public record AuthenticatedApiClient(
        String username,
        Cookie csrfCookie,
        String csrfToken,
        String accessToken,
        Cookie refreshCookie) {}
