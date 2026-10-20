package vn.edu.utc.tankbattle.common.event;

/** sessionId khác null: thu hồi một phiên; userId khác null: thu hồi mọi phiên API của user. */
public record ApiSessionsRevokedEvent(String sessionId, Long userId) {}
