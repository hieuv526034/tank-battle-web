package vn.edu.utc.tankbattle.auth.model;

/** Bucket bất biến, cập nhật bằng ConcurrentHashMap.compute để không tranh count. */
public record RateWindow(long startedAt, int count) {}
