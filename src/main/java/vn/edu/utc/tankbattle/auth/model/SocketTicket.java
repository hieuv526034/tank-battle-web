package vn.edu.utc.tankbattle.auth.model;

import java.time.Instant;
import vn.edu.utc.tankbattle.common.model.SocketIdentity;

/** Bản ghi vé tạm phía server, tách khỏi service để dễ đọc và kiểm thử. */
public record SocketTicket(SocketIdentity identity, Instant deadline) {}
