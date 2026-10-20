package vn.edu.utc.tankbattle.auth.model;

/** Dữ liệu nội bộ khi seed local, không phải response và không chứa mật khẩu thô. */
public record LocalDemoAccount(String username, String displayName, String passwordHash) {}
