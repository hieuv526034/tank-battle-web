package vn.edu.utc.tankbattle.business.model;

import java.util.List;

/** Mỗi cửa gồm các đợt; danh sách mã địch tham chiếu catalog đã validate khi startup. */
public record CampaignStage(int number, String name, int durationTicks, List<List<String>> waves) {}
