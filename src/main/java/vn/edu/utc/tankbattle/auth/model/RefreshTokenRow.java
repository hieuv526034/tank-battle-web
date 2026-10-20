package vn.edu.utc.tankbattle.auth.model;

import java.time.Instant;

/** Projection nội bộ của refresh token đã băm, gồm hạn dùng và mốc consume để phát hiện replay. */
public record RefreshTokenRow(
        String id, String sessionId, String tokenHash, Instant expiresAt, Instant consumedAt) {}
