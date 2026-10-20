package vn.edu.utc.tankbattle.business.model;

/** Buff cùng loại được làm mới thời hạn, không cộng dồn vô hạn. expiresAtTick là tick tuyệt đối. */
public record BuffState(int amount, long expiresAtTick) {}
