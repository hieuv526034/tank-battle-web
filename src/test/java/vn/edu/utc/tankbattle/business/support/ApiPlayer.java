package vn.edu.utc.tankbattle.business.support;

/** Danh tính fixture, access JWT thật do TokenService cấp; không in token ra log test. */
public record ApiPlayer(long id, String username, String accessToken) {}
