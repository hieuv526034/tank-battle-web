package vn.edu.utc.tankbattle.auth.model;

/** Projection chỉ mang trạng thái để bootstrap kiểm tra, không tải hoặc trả password hash. */
public record BootstrapAdminAccount(String role, boolean enabled, boolean passwordLogin) {}
