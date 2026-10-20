package vn.edu.utc.tankbattle.auth.model;

import java.time.Instant;
import vn.edu.utc.tankbattle.auth.dto.response.AuthTokenResponse;

/** Chỉ dùng nội bộ để controller đặt refresh vào HttpOnly cookie, không serialize model này. */
public record TokenGrant(AuthTokenResponse access, String refreshToken, Instant refreshExpiresAt) {}
