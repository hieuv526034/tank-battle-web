package vn.edu.utc.tankbattle.business.engine;

import java.util.*;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants.*;

/** AI, wave và item chạy bằng tick; catalog và seed đều nằm trong checkpoint để replay ổn định. */
public final class CampaignEngine {
    /** Giữ thuật toán Campaign dạng hàm static; mọi trạng thái cần lưu đều nằm trong GameState. */
    private CampaignEngine() {}

    /**
     * Tạo các cá thể địch của wave hiện tại từ code trong stage và catalog đã chụp vào trận. Nhân
     * HP/tốc độ/cooldown/điểm theo độ khó, tìm spawn hợp lệ với số lần thử giới hạn rồi thêm item
     * hỗ trợ. Bộ đếm ID, seed và thông số địch thực tế được lưu trong state để wave không đổi khi
     * restore.
     */
    public static void spawnWave(GameState state) {
        List<String> codes = state.stage.waves().get(state.wave);
        Map<String, EnemyDefinition> catalog = new HashMap<>();
        state.content.enemies().forEach(definition -> catalog.put(definition.code(), definition));
        for (String code : codes) {
            EnemyDefinition original = catalog.get(code);
            double health =
                    state.difficulty == Difficulty.EASY
                            ? 0.75
                            : state.difficulty == Difficulty.HARD ? 1.5 : 1;
            double speed =
                    state.difficulty == Difficulty.EASY
                            ? 0.8
                            : state.difficulty == Difficulty.HARD ? 1.15 : 1;
            EnemyDefinition definition =
                    new EnemyDefinition(
                            original.code(),
                            original.displayName(),
                            original.renderKey(),
                            original.movement(),
                            original.behavior(),
                            Math.max(1, (int) Math.ceil(original.health() * health)),
                            original.speed() * speed,
                            original.contactDamage(),
                            original.projectileDamage(),
                            original.fireCooldownTicks() == 0
                                    ? 0
                                    : Math.max(1, (int) (original.fireCooldownTicks() / speed)),
                            original.hitboxRadius(),
                            original.killScore() * (state.difficulty == Difficulty.HARD ? 2 : 1));
            EnemyState enemy = new EnemyState();
            enemy.id = "E" + state.nextEnemyId++;
            enemy.definition = definition;
            enemy.hp = definition.health();
            // Tìm điểm hợp lệ có khoảng cách với người chơi; vòng lặp giới hạn tránh kẹt engine.
            for (int attempt = 0; attempt < 100; attempt++) {
                enemy.x = 32 + CombatGeometry.random(state) * (state.rules.width - 64);
                enemy.y = 32 + CombatGeometry.random(state) * (state.rules.height - 64);
                if (CombatGeometry.clear(
                                state,
                                enemy.x,
                                enemy.y,
                                definition.hitboxRadius(),
                                definition.movement() == EnemyMovement.AIR)
                        && state.players.values().stream()
                                .noneMatch(p -> Math.hypot(p.x - enemy.x, p.y - enemy.y) < 100)
                        && state.enemies.values().stream()
                                .noneMatch(e -> Math.hypot(e.x - enemy.x, e.y - enemy.y) < 40))
                    break;
            }
            state.enemies.put(enemy.id, enemy);
        }
        // Mỗi wave có một item hỗ trợ cạnh spawn đầu; người còn sống cần tự di chuyển tới nhặt.
        if (!state.content.items().isEmpty()) {
            SpawnPoint spawn = state.rules.spawns.getFirst();
            ItemDefinition definition =
                    state.content.items().get(state.wave % state.content.items().size());
            drop(state, definition, spawn.x + 40, spawn.y);
        }
    }

    /**
     * Chạy một bước AI cho các địch đang sống ở Campaign. Chọn người sống gần nhất, đổi hướng theo
     * CHASE/RANGED/PATROL, kiểm tra AIR/GROUND khi di chuyển và áp cooldown bắn/tiếp xúc. Dùng
     * tick/seed trong state; không query DB và không đọc lại file cấu hình.
     */
    public static void step(GameState state) {
        if (state.mode != GameMode.CAMPAIGN) return;
        double dt = state.rules.tickMs / 1000.0;
        for (EnemyState enemy : state.enemies.values()) {
            enemy.cooldown = Math.max(0, enemy.cooldown - 1);
            enemy.contactCooldown = Math.max(0, enemy.contactCooldown - 1);
            PlayerState target =
                    state.players.values().stream()
                            .filter(p -> p.hp > 0)
                            .min(
                                    Comparator.comparingDouble(
                                            p -> Math.hypot(p.x - enemy.x, p.y - enemy.y)))
                            .orElse(null);
            if (target == null) continue;
            if (state.tick >= enemy.thinkAt) {
                enemy.thinkAt = state.tick + (state.difficulty == Difficulty.HARD ? 8 : 16);
                if (enemy.definition.behavior() == EnemyBehavior.PATROL)
                    enemy.direction = CombatGeometry.randomDirection(state);
                else
                    enemy.direction =
                            Math.abs(target.x - enemy.x) > Math.abs(target.y - enemy.y)
                                    ? (target.x < enemy.x ? "LEFT" : "RIGHT")
                                    : (target.y < enemy.y ? "UP" : "DOWN");
            }
            double[] vector = CombatGeometry.vector(enemy.direction);
            double x = enemy.x + vector[0] * enemy.definition.speed() * dt,
                    y = enemy.y + vector[1] * enemy.definition.speed() * dt;
            if (CombatGeometry.clear(
                    state,
                    x,
                    y,
                    enemy.definition.hitboxRadius(),
                    enemy.definition.movement() == EnemyMovement.AIR)) {
                enemy.x = x;
                enemy.y = y;
            } else {
                enemy.direction = CombatGeometry.randomDirection(state);
                enemy.thinkAt = state.tick + 10;
            }
            if (enemy.definition.projectileDamage() > 0 && enemy.cooldown == 0) {
                GameEngine.fire(
                        state,
                        enemy.id,
                        enemy.x,
                        enemy.y,
                        enemy.direction,
                        enemy.definition.hitboxRadius(),
                        enemy.definition.projectileDamage(),
                        true);
                enemy.cooldown = enemy.definition.fireCooldownTicks();
            }
            if (enemy.definition.contactDamage() > 0
                    && enemy.contactCooldown == 0
                    && Math.hypot(target.x - enemy.x, target.y - enemy.y)
                            < state.rules.tankHalf + enemy.definition.hitboxRadius()) {
                GameEngine.damage(state, target, enemy.definition.contactDamage());
                enemy.contactCooldown = 20;
            }
        }
    }

    /**
     * Cộng đúng một kill và killScore cho người được credit khi caller đã loại địch khỏi state. Sau
     * đó dùng PRNG của trận để quyết định xác suất/loại item rơi; owner đạn không còn là player thì
     * không cộng điểm cho user.
     */
    public static void enemyKilled(GameState state, EnemyState enemy, PlayerState killer) {
        if (killer != null) {
            killer.kills++;
            killer.score += enemy.definition.killScore();
        }
        if (!state.content.items().isEmpty() && CombatGeometry.random(state) < 0.5) {
            int index = (int) (CombatGeometry.random(state) * state.content.items().size());
            drop(state, state.content.items().get(index), enemy.x, enemy.y);
        }
    }

    /**
     * Thêm item vào map nếu chưa đạt trần 32 vật phẩm. Ghi định nghĩa, vị trí, ID và hạn tick+600
     * để item tự hết hạn và phục hồi đúng tuổi còn lại.
     */
    public static void drop(GameState state, ItemDefinition definition, double x, double y) {
        if (state.items.size() >= 32) return;
        ItemState item = new ItemState();
        item.id = state.nextItemId++;
        item.definition = definition;
        item.x = x;
        item.y = y;
        item.expiresAtTick = state.tick + 600;
        state.items.add(item);
    }

    /**
     * Loại item hết hạn rồi kiểm tra người sống đủ gần để nhận từng item. Thứ tự player trong
     * TreeMap phân xử khi nhiều người chạm cùng tick; mỗi item chỉ nhận một lần. REPAIR không vượt
     * maxHP; buff khác thay amount và làm mới expiresAtTick, không cộng dồn vô hạn.
     */
    public static void collect(GameState state) {
        Iterator<ItemState> items = state.items.iterator();
        while (items.hasNext()) {
            ItemState item = items.next();
            if (item.expiresAtTick <= state.tick) {
                items.remove();
                continue;
            }
            // TreeMap tạo thứ tự ổn định khi hai người chạm item cùng tick.
            for (PlayerState player : state.players.values()) {
                if (player.hp <= 0
                        || Math.hypot(player.x - item.x, player.y - item.y)
                                > state.rules.tankHalf + 8) continue;
                ItemDefinition definition = item.definition;
                if (definition.effect() == ItemType.REPAIR)
                    player.hp = Math.min(state.rules.hp, player.hp + definition.amount());
                else
                    player.buffs.put(
                            definition.effect(),
                            new BuffState(
                                    definition.amount(), state.tick + definition.durationTicks()));
                items.remove();
                break;
            }
        }
    }

    /**
     * Kiểm tra đội còn sống, wave còn địch và thời lượng cửa sau tick. Hết địch thì tăng wave/spawn
     * tiếp hoặc CAMPAIGN_CLEARED; hết người sống/hết giờ thì ENDING thất bại.
     */
    public static void evaluate(GameState state) {
        if (state.players.values().stream().noneMatch(p -> p.hp > 0)) end(state, "TEAM_ELIMINATED");
        else if (state.enemies.isEmpty()) {
            if (++state.wave >= state.stage.waves().size()) end(state, "CAMPAIGN_CLEARED");
            else spawnWave(state);
        } else if (state.remainingTicks <= 0) end(state, "TIME_LIMIT");
    }

    /**
     * Ghi phase ENDING và mã lý do kết thúc Campaign vào state. Coordinator sẽ chụp state để
     * MatchService kết toán; hàm này chưa làm kết quả bền trong DB.
     */
    private static void end(GameState state, String reason) {
        state.phase = "ENDING";
        state.endReason = reason;
    }
}
