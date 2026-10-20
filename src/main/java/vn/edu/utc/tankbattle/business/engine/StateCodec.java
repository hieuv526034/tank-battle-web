package vn.edu.utc.tankbattle.business.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.business.model.BulletState;
import vn.edu.utc.tankbattle.business.model.EnemyState;
import vn.edu.utc.tankbattle.business.model.GameRules;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.business.model.ItemState;
import vn.edu.utc.tankbattle.business.model.PlayerState;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;

/** Điểm duy nhất serialize/validate checkpoint; thay schema cần sửa và test tại đây. */
@Component
public class StateCodec {
    private final ObjectMapper json;

    /**
     * Nhận ObjectMapper dùng chung để mã hóa toàn bộ state và giữ định dạng JSON nhất quán với API.
     */
    public StateCodec(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Đọc luật gốc/bản đồ từ game-rules.json khi tạo trận hoặc trả catalog. Luật sẽ được gắn vào
     * state để checkpoint tự đủ dữ liệu, không lấy file mới thay luật của trận đang khôi phục.
     */
    public GameRules rules() {
        try (InputStream input = new ClassPathResource("static/game-rules.json").getInputStream()) {
            return json.readValue(input, GameRules.class);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được game-rules.json", e);
        }
    }

    /**
     * Serialize state thành JSON cho snapshot/bản lưu; không tự thay đổi state hoặc ghi DB. Caller
     * chụp state dưới monitor runtime trước khi đưa sang luồng persistence.
     */
    public String write(GameState state) {
        try {
            return json.writeValueAsString(state);
        } catch (IOException e) {
            throw new IllegalStateException("Không serialize được state", e);
        }
    }

    /**
     * Giải mã và kiểm tra checkpoint trước khi cho runtime sử dụng. Giới hạn kích thước, nâng Arena
     * schema 1 sang 2, kiểm tra luật/phase/số thực thể/HP/hướng/buff và dựng lại TreeMap để giữ thứ
     * tự xác định. State sai bị từ chối sớm thay vì để mỗi tick ném lỗi; Coordinator hủy checkpoint
     * hỏng, save API trả lỗi SAVE_INVALID.
     */
    public GameState read(String payload) {
        try {
            if (payload == null || payload.length() > 1_000_000)
                throw new IllegalArgumentException("Checkpoint rỗng/quá lớn");
            GameState s = json.readValue(payload, GameState.class);
            if (s == null || s.rules == null)
                throw new IllegalArgumentException("Checkpoint thiếu luật");
            validateRules(s.rules);
            // Schema 1 là Arena cũ; các trường schema 2 có giá trị mặc định tương thích.
            if (s.schemaVersion == 1) s.schemaVersion = 2;
            if (s.schemaVersion != 2
                    || s.rules == null
                    || s.rules.version != 1
                    || s.matchId == null
                    || s.mode == null
                    || s.difficulty == null
                    || s.players == null
                    || s.players.isEmpty()
                    || (s.mode == GameMode.ARENA && s.players.size() < 2)
                    || s.players.size() > 4
                    || s.bullets.size() > 64
                    || s.tick < 0
                    || s.remainingTicks < 0
                    || s.remainingTicks
                            > (s.stage == null ? s.rules.durationTicks : s.stage.durationTicks())
                    || s.enemies == null
                    || s.enemies.size() > 16
                    || s.items == null
                    || s.items.size() > 32
                    || (s.mode == GameMode.CAMPAIGN
                            && (s.stage == null
                                    || s.content == null
                                    || s.wave < 0
                                    || s.wave > s.stage.waves().size()))
                    || s.rules.tickMs != 50
                    || !java.util.Set.of("RUNNING", "ENDING", "FINISHED").contains(s.phase))
                throw new IllegalArgumentException("Checkpoint không tương thích");
            for (PlayerState p : s.players.values())
                if (p == null
                        || p.id == null
                        || !s.players.containsKey(p.id)
                        || !Double.isFinite(p.x)
                        || !Double.isFinite(p.y)
                        || p.hp < 0
                        || p.hp > s.rules.hp
                        || p.score < 0
                        || p.kills < 0
                        || p.buffs == null
                        || p.buffs.size() > 5
                        || !direction(p.direction, false)
                        || !direction(p.botDirection, true)
                        || p.buffs.values().stream()
                                .anyMatch(
                                        buff ->
                                                buff == null
                                                        || buff.amount() < 1
                                                        || buff.amount() > 100
                                                        || buff.expiresAtTick() < 0))
                    throw new IllegalArgumentException("PlayerState không hợp lệ");
            for (EnemyState enemy : s.enemies.values())
                if (enemy == null
                        || enemy.definition == null
                        || enemy.id == null
                        || !s.enemies.containsKey(enemy.id)
                        || !Double.isFinite(enemy.x)
                        || !Double.isFinite(enemy.y)
                        || enemy.hp <= 0
                        || enemy.hp > enemy.definition.health()
                        || !direction(enemy.direction, false))
                    throw new IllegalArgumentException("EnemyState không hợp lệ");
            for (BulletState bullet : s.bullets)
                if (bullet == null
                        || !Double.isFinite(bullet.x)
                        || !Double.isFinite(bullet.y)
                        || bullet.damage < 1
                        || bullet.damage > 200
                        || bullet.life < 1
                        || bullet.owner == null
                        || !direction(bullet.direction, false))
                    throw new IllegalArgumentException("BulletState không hợp lệ");
            for (ItemState item : s.items)
                if (item == null
                        || item.definition == null
                        || !Double.isFinite(item.x)
                        || !Double.isFinite(item.y))
                    throw new IllegalArgumentException("ItemState không hợp lệ");
            s.players = new java.util.TreeMap<>(s.players);
            s.enemies = new java.util.TreeMap<>(s.enemies);
            return s;
        } catch (IOException e) {
            throw new IllegalArgumentException("Checkpoint không hợp lệ", e);
        }
    }

    /**
     * Tạo bản sao sâu bằng cùng đường write/read, đồng thời chạy kiểm tra checkpoint. Dùng khi tách
     * state đang thay đổi khỏi queue ghi/gửi socket để các luồng không cùng sửa một object.
     */
    public GameState copy(GameState state) {
        return read(write(state));
    }

    /**
     * Kiểm tra hướng thuộc bốn hướng di chuyển; allowNone mở thêm NONE cho input/hướng nghĩ của
     * bot. Hướng không hợp lệ trong bản lưu bị chặn trước khi switch hình học chạy.
     */
    private boolean direction(String direction, boolean allowNone) {
        return direction != null
                && (java.util.Set.of("UP", "DOWN", "LEFT", "RIGHT").contains(direction)
                        || allowNone && direction.equals("NONE"));
    }

    /**
     * Kiểm tra biên kích thước map, HP, tốc độ, tick, cooldown và dữ liệu tường/spawn của
     * checkpoint. Các giới hạn ngăn state lỗi hoặc quá lớn đi vào vòng mô phỏng; lỗi chẩn đoán được
     * chuyển thành mã custom ở entry point.
     */
    private void validateRules(GameRules rules) {
        if (rules.version != 1
                || rules.tickMs != 50
                || rules.width < 128
                || rules.width > 4096
                || rules.height < 128
                || rules.height > 4096
                || rules.hp < 1
                || rules.hp > 100
                || rules.durationTicks < 1
                || rules.durationTicks > 36000
                || !Double.isFinite(rules.tankSpeed)
                || rules.tankSpeed <= 0
                || rules.tankSpeed > 500
                || !Double.isFinite(rules.bulletSpeed)
                || rules.bulletSpeed <= 0
                || rules.bulletSpeed > 10000
                || !Double.isFinite(rules.tankHalf)
                || rules.tankHalf <= 0
                || rules.tankHalf > 32
                || !Double.isFinite(rules.bulletRadius)
                || rules.bulletRadius < 0
                || rules.bulletRadius > 16
                || rules.fireCooldownTicks < 1
                || rules.bulletLifeTicks < 1
                || rules.spawns == null
                || rules.spawns.size() != 4
                || rules.walls == null
                || rules.walls.size() > 1000)
            throw new IllegalArgumentException("GameRules không hợp lệ");
        if (rules.spawns.stream()
                        .anyMatch(
                                point ->
                                        point == null
                                                || !Double.isFinite(point.x)
                                                || !Double.isFinite(point.y))
                || rules.walls.stream()
                        .anyMatch(
                                wall ->
                                        wall == null
                                                || !Double.isFinite(wall.x)
                                                || !Double.isFinite(wall.y)
                                                || !Double.isFinite(wall.w)
                                                || !Double.isFinite(wall.h)
                                                || wall.w <= 0
                                                || wall.h <= 0))
            throw new IllegalArgumentException("Bản đồ không hợp lệ");
    }
}
