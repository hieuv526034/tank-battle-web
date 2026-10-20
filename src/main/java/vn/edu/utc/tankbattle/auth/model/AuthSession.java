package vn.edu.utc.tankbattle.auth.model;

import java.time.Instant;

/** Projection nội bộ khi khóa phiên để rotate token. Không trả trực tiếp cho trình duyệt. */
public record AuthSession(
        String id,
        long userId,
        String username,
        String role,
        boolean enabled,
        Instant expiresAt,
        Instant revokedAt) {}
