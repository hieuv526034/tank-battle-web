package vn.edu.utc.tankbattle.business.model;

/** Một kết quả đã tính trong RAM, dùng làm đầu vào batch update của repository. */
public record MatchPlayerResult(long userId, String outcome, int hp, int kills, int score) {}
