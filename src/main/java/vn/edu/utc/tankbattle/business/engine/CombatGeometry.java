package vn.edu.utc.tankbattle.business.engine;

import vn.edu.utc.tankbattle.business.model.*;

/** Hình học thuần: kiểm tra tường và giao cắt đoạn bay, không phụ thuộc framework/DB. */
public final class CombatGeometry {
    /** Ẩn constructor của nhóm hàm hình học/PRNG không có trạng thái riêng. */
    private CombatGeometry() {}

    /**
     * Đổi hướng điều khiển sang vector đơn vị dx/dy; NONE nhận vector 0. Engine nhân vector với tốc
     * độ và tick duration để tính quãng đường.
     */
    public static double[] vector(String direction) {
        return switch (direction) {
            case "UP" -> new double[] {0, -1};
            case "DOWN" -> new double[] {0, 1};
            case "LEFT" -> new double[] {-1, 0};
            case "RIGHT" -> new double[] {1, 0};
            default -> new double[] {0, 0};
        };
    }

    /**
     * Kiểm tra hitbox dự kiến nằm trong biên map và, với GROUND, không chồng hình chữ nhật tường.
     * AIR chỉ kiểm tra biên; va chạm với xe/mục tiêu khác do engine xử lý riêng.
     *
     * @param half bán kính nửa hitbox quanh tọa độ x/y
     * @return true nếu vị trí được phép xét di chuyển
     */
    public static boolean clear(GameState state, double x, double y, double half, boolean air) {
        if (x < half || y < half || x > state.rules.width - half || y > state.rules.height - half)
            return false;
        if (!air)
            for (WallState wall : state.rules.walls)
                if (x + half > wall.x
                        && x - half < wall.x + wall.w
                        && y + half > wall.y
                        && y - half < wall.y + wall.h) return false;
        return true;
    }

    /**
     * Tìm thời điểm giao cắt sớm nhất của đoạn bay với một hình chữ nhật bằng thuật toán slab. Kết
     * quả 0–1 là tỷ lệ trên đoạn; vô cực khi không giao. So giá trị này giữa các vật cản giúp đạn
     * dừng ở mục tiêu gần nhất. Xét cả đoạn thay vì chỉ điểm cuối để đạn nhanh không xuyên tường/xe
     * giữa hai tick.
     */
    public static double hit(
            double x, double y, double nx, double ny, double rx, double ry, double w, double h) {
        double low = 0, high = 1;
        double[] start = {x, y}, delta = {nx - x, ny - y}, min = {rx, ry}, max = {rx + w, ry + h};
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(delta[axis]) < 1e-9) {
                if (start[axis] < min[axis] || start[axis] > max[axis])
                    return Double.POSITIVE_INFINITY;
            } else {
                double a = (min[axis] - start[axis]) / delta[axis],
                        b = (max[axis] - start[axis]) / delta[axis];
                low = Math.max(low, Math.min(a, b));
                high = Math.min(high, Math.max(a, b));
                if (low > high) return Double.POSITIVE_INFINITY;
            }
        }
        return low;
    }

    /**
     * Sinh số giả ngẫu nhiên trong khoảng từ 0 đến nhỏ hơn 1 bằng LCG và cập nhật seed ngay trong
     * GameState. AI/item dùng cùng chuỗi này để kết quả sau checkpoint tái hiện được khi input
     * giống nhau.
     */
    public static double random(GameState state) {
        state.seed = (state.seed * 1664525L + 1013904223L) & 0xffffffffL;
        return state.seed / 4294967296.0;
    }

    /** Chọn một trong bốn hướng từ PRNG của trận; mỗi lần gọi tiêu thụ một bước seed. */
    public static String randomDirection(GameState state) {
        return new String[] {"UP", "DOWN", "LEFT", "RIGHT"}[(int) (random(state) * 4)];
    }
}
