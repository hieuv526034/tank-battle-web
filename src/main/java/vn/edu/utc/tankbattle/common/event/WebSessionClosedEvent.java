package vn.edu.utc.tankbattle.common.event;

/** Auth phát sự kiện khi logout; nghiệp vụ đóng kết nối liên quan mà auth không gọi SocketHub. */
public record WebSessionClosedEvent(String sessionId) {}
