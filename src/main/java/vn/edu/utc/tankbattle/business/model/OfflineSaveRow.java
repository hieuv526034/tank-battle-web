package vn.edu.utc.tankbattle.business.model;

import java.time.Instant;

/** Projection SQL nội bộ; chỉ chủ bản lưu đọc được qua API. */
public record OfflineSaveRow(
        int slot, long revision, String payload, Instant updatedAt, boolean deleted) {}
