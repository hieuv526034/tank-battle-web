package vn.edu.utc.tankbattle.common.model;

import java.time.Instant;

/** Danh tính đã xác thực tại handshake; business không cần phụ thuộc lớp JWT/OIDC của auth. */
public record SocketIdentity(
        long userId, String username, String authSessionId, Instant expiresAt) {}
