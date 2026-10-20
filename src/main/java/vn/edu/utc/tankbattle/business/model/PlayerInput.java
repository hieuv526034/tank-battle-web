package vn.edu.utc.tankbattle.business.model;

import java.util.Set;

/** Ý định điều khiển đã tách khỏi engine; không nhận tọa độ/HP/kết quả do client tự khai. */
public record PlayerInput(String direction, boolean fire) {
    public static final PlayerInput NONE = new PlayerInput("NONE", false);
    private static final Set<String> DIRECTIONS = Set.of("UP", "DOWN", "LEFT", "RIGHT", "NONE");

    public boolean valid() {
        return direction != null && DIRECTIONS.contains(direction);
    }
}
