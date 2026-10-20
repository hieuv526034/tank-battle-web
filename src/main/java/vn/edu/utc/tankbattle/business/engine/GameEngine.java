package vn.edu.utc.tankbattle.business.engine;

import java.util.*;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants.*;

/**
 * Engine thuần Java: server nhận ý định, tự tính chuyển động/đạn/HP/điểm; tuyệt đối không gọi DB.
 */
public final class GameEngine {
    /**
     * Lớp thuật toán chỉ có hàm static; giữ constructor private để caller thao tác state thay vì
     * tạo một engine có trạng thái ẩn.
     */
    private GameEngine() {}

    /**
     * Tạo Arena mặc định từ luật và roster để dùng chung cho mô phỏng/fixture. Ủy quyền sang
     * overload đầy đủ với mode ARENA và difficulty NORMAL.
     */
    public static GameState create(String matchId, GameRules rules, List<PlayerState> players) {
        return create(matchId, rules, players, null, GameMode.ARENA, Difficulty.NORMAL, null);
    }

    /**
     * Khởi tạo checkpoint đầu từ roster do server chọn và bộ luật/catalog đã chốt trước trận. Kiểm
     * tra số người theo chế độ, đặt HP/vị trí spawn và khởi wave đầu nếu là Campaign.
     *
     * @param matchId ID trận do server tạo, giữ nguyên khi lưu/khôi phục
     * @param rules luật/bản đồ cần giữ trong checkpoint
     * @param players các cá thể người chơi ban đầu
     * @param content catalog địch/item của trận
     * @param mode ARENA đối kháng hoặc CAMPAIGN hợp tác
     * @param difficulty độ khó AI/điểm Campaign
     * @param stage cấu hình cửa Campaign, null cho Arena
     * @return state thuần dữ liệu, chưa có thao tác DB
     */
    public static GameState create(
            String matchId,
            GameRules rules,
            List<PlayerState> players,
            GameContentDefinition content,
            GameMode mode,
            Difficulty difficulty,
            CampaignStage stage) {
        if (players.isEmpty()
                || players.size() > 4
                || (mode == GameMode.ARENA && players.size() < 2)
                || (mode == GameMode.CAMPAIGN && (stage == null || content == null)))
            throw new IllegalArgumentException("Roster/chế độ không hợp lệ");
        GameState state = new GameState();
        state.matchId = matchId;
        state.rules = rules;
        state.content = content;
        state.mode = mode;
        state.difficulty = difficulty;
        state.stage = stage;
        state.remainingTicks = stage == null ? rules.durationTicks : stage.durationTicks();
        for (int index = 0; index < players.size(); index++) {
            PlayerState player = players.get(index);
            SpawnPoint spawn = rules.spawns.get(index);
            player.x = spawn.x;
            player.y = spawn.y;
            player.hp = rules.hp;
            state.players.put(player.id, player);
        }
        if (mode == GameMode.CAMPAIGN) CampaignEngine.spawnWave(state);
        return state;
    }

    /**
     * Mô phỏng đúng một tick khi phase còn RUNNING, không phụ thuộc đồng hồ/FPS của client. Thứ tự:
     * hết hạn buff → input/di chuyển/bắn → AI/contact → đạn → nhặt item → đánh giá kết thúc. Mọi
     * thời hạn gameplay dùng tick và mọi ngẫu nhiên dùng seed trong state để restore rồi chạy tiếp
     * cho cùng kết quả.
     */
    public static void step(GameState state, Map<String, PlayerInput> inputs) {
        if (!state.phase.equals("RUNNING")) return;
        state.tick++;
        state.remainingTicks = Math.max(0, state.remainingTicks - 1);
        double dt = state.rules.tickMs / 1000.0;
        // Xử lý input bằng luật server. Client không được gửi vị trí, HP hay damage để ghi vào
        // state.
        for (PlayerState player : state.players.values()) {
            player.buffs
                    .entrySet()
                    .removeIf(entry -> entry.getValue().expiresAtTick() <= state.tick);
            if (player.hp <= 0) continue;
            player.cooldown = Math.max(0, player.cooldown - 1);
            PlayerInput input =
                    player.bot
                            ? botInput(state, player)
                            : inputs.getOrDefault(player.id, PlayerInput.NONE);
            if (!input.valid()) input = PlayerInput.NONE;
            if (!input.direction().equals("NONE")) {
                player.direction = input.direction();
                double[] vector = CombatGeometry.vector(input.direction());
                double speed = state.rules.tankSpeed * (1 + buff(player, ItemType.SPEED) / 100.0);
                double x = player.x + vector[0] * speed * dt, y = player.y + vector[1] * speed * dt;
                if (canMove(state, player, x, y)) {
                    player.x = x;
                    player.y = y;
                }
            }
            if (input.fire() && player.cooldown == 0) {
                fire(
                        state,
                        player.id,
                        player.x,
                        player.y,
                        player.direction,
                        state.rules.tankHalf,
                        1 + buff(player, ItemType.DAMAGE_BOOST),
                        false);
                player.cooldown =
                        Math.max(
                                1,
                                (int)
                                        Math.ceil(
                                                state.rules.fireCooldownTicks
                                                        * Math.max(
                                                                0.1,
                                                                1
                                                                        - buff(
                                                                                        player,
                                                                                        ItemType
                                                                                                .RAPID_FIRE)
                                                                                / 100.0)));
            }
        }
        // AI/contact chạy trước đạn; nhặt item và xét kết thúc sau khi áp sát thương của cả tick.
        CampaignEngine.step(state);
        moveBullets(state);
        CampaignEngine.collect(state);
        evaluate(state);
    }

    /**
     * Tạo đạn tại đầu nòng nếu chưa vượt giới hạn đạn đang tồn tại. Lưu owner/faction và damage
     * ngay lúc bắn để buff hết hạn sau đó không đổi sát thương đạn đã bay. Cooldown thuộc
     * player/enemy được caller cập nhật sau bước này.
     */
    public static void fire(
            GameState state,
            String owner,
            double x,
            double y,
            String direction,
            double radius,
            int damage,
            boolean enemy) {
        if (state.bullets.size() >= 64) return;
        BulletState bullet = new BulletState();
        bullet.id = state.nextBulletId++;
        bullet.owner = owner;
        bullet.direction = direction;
        bullet.life = state.rules.bulletLifeTicks;
        bullet.damage = damage;
        bullet.enemy = enemy;
        double[] vector = CombatGeometry.vector(direction);
        bullet.x = x + vector[0] * (radius + 4);
        bullet.y = y + vector[1] * (radius + 4);
        state.bullets.add(bullet);
    }

    /**
     * Dịch chuyển đạn bằng giao cắt đoạn bay, chọn va chạm gần nhất với tường hoặc mục tiêu hợp lệ.
     * Campaign chặn friendly fire giữa người chơi. Gom damage trong tick rồi áp một lần để xử lý
     * cùng bị loại và không ghi hai kill cho một địch. Nếu nhiều owner cùng trúng, credit thuộc
     * owner của đạn xử lý sau cùng theo thứ tự list ổn định trong checkpoint.
     */
    private static void moveBullets(GameState state) {
        Map<String, Integer> damages = new TreeMap<>();
        Map<String, String> credits = new HashMap<>();
        List<BulletState> surviving = new ArrayList<>();
        for (BulletState bullet : state.bullets) {
            double[] vector = CombatGeometry.vector(bullet.direction);
            double nx =
                    bullet.x + vector[0] * state.rules.bulletSpeed * state.rules.tickMs / 1000.0;
            double ny =
                    bullet.y + vector[1] * state.rules.bulletSpeed * state.rules.tickMs / 1000.0;
            double first = Double.POSITIVE_INFINITY, radius = state.rules.bulletRadius;
            String target = null;
            // first là tỷ lệ trên đoạn bay [0,1]. Tường gần hơn sẽ che mục tiêu ở phía sau.
            for (WallState wall : state.rules.walls)
                first =
                        Math.min(
                                first,
                                CombatGeometry.hit(
                                        bullet.x,
                                        bullet.y,
                                        nx,
                                        ny,
                                        wall.x - radius,
                                        wall.y - radius,
                                        wall.w + 2 * radius,
                                        wall.h + 2 * radius));
            // Arena cho phép đấu người; Campaign không cho đạn người chơi trúng người chơi khác.
            if (bullet.enemy || state.mode == GameMode.ARENA)
                for (PlayerState player : state.players.values()) {
                    if (player.hp <= 0 || player.id.equals(bullet.owner)) continue;
                    double half = state.rules.tankHalf + radius;
                    double at =
                            CombatGeometry.hit(
                                    bullet.x,
                                    bullet.y,
                                    nx,
                                    ny,
                                    player.x - half,
                                    player.y - half,
                                    2 * half,
                                    2 * half);
                    if (at < first) {
                        first = at;
                        target = player.id;
                    }
                }
            if (!bullet.enemy)
                for (EnemyState enemy : state.enemies.values()) {
                    double half = enemy.definition.hitboxRadius() + radius;
                    double at =
                            CombatGeometry.hit(
                                    bullet.x,
                                    bullet.y,
                                    nx,
                                    ny,
                                    enemy.x - half,
                                    enemy.y - half,
                                    2 * half,
                                    2 * half);
                    if (at < first) {
                        first = at;
                        target = enemy.id;
                    }
                }
            if (first != Double.POSITIVE_INFINITY) {
                if (target != null) {
                    damages.merge(target, bullet.damage, Integer::sum);
                    credits.put(
                            target, bullet.owner); // Một kill chỉ được ghi ở bước áp dụng damage.
                }
                continue;
            }
            bullet.x = nx;
            bullet.y = ny;
            bullet.life--;
            if (bullet.life > 0
                    && nx >= 0
                    && ny >= 0
                    && nx <= state.rules.width
                    && ny <= state.rules.height) surviving.add(bullet);
        }
        state.bullets = surviving;
        // Áp tổng damage một lần/mục tiêu: hai xe cùng chết vẫn được xét trong cùng tick.
        for (Map.Entry<String, Integer> entry : damages.entrySet()) {
            PlayerState player = state.players.get(entry.getKey());
            PlayerState killer = state.players.get(credits.get(entry.getKey()));
            if (player != null) {
                int previous = player.hp;
                damage(state, player, entry.getValue());
                if (previous > 0 && player.hp == 0 && killer != null) {
                    killer.kills++;
                    killer.score += 100;
                }
            } else {
                EnemyState enemy = state.enemies.get(entry.getKey());
                if (enemy != null) {
                    enemy.hp = Math.max(0, enemy.hp - entry.getValue());
                    if (enemy.hp == 0) {
                        state.enemies.remove(enemy.id);
                        CampaignEngine.enemyKilled(state, enemy, killer);
                    }
                }
            }
        }
    }

    /**
     * Áp một lượng damage sau khi trừ buff SHIELD hiện có và chặn HP về tối thiểu 0. Nếu HP vừa
     * xuống 0 thì ghi eliminatedAtTick, dùng phân biệt chết đồng thời ở tick cuối với người đã bị
     * loại trước đó.
     */
    public static void damage(GameState state, PlayerState player, int amount) {
        int previous = player.hp;
        player.hp = Math.max(0, player.hp - Math.max(0, amount - buff(player, ItemType.SHIELD)));
        if (previous > 0 && player.hp == 0) player.eliminatedAtTick = state.tick;
    }

    /**
     * Đọc amount của một loại buff đang nằm trong state, trả 0 khi không có. step đã loại buff hết
     * hạn trước khi dùng; helper phục vụ tốc độ, cooldown, damage và shield.
     */
    public static int buff(PlayerState player, ItemType type) {
        BuffState buff = player.buffs.get(type);
        return buff == null ? 0 : buff.amount();
    }

    /**
     * Xác định điều kiện kết thúc sau tick mà không ghi DB. Arena kết thúc khi còn tối đa một xe
     * sống hoặc hết giờ; Campaign ủy quyền kiểm tra wave/đội cho CampaignEngine. Chỉ đổi phase sang
     * ENDING, Coordinator còn phải lưu kết quả thành công trước khi công bố chính thức.
     */
    public static void evaluate(GameState state) {
        if (!"RUNNING".equals(state.phase)) return;
        if (state.mode == GameMode.CAMPAIGN) {
            CampaignEngine.evaluate(state);
            return;
        }
        long alive = state.players.values().stream().filter(player -> player.hp > 0).count();
        if (alive <= 1 || state.remainingTicks <= 0) {
            state.phase = "ENDING";
            state.endReason =
                    alive == 1 ? "LAST_SURVIVOR" : alive == 0 ? "MUTUAL_ELIMINATION" : "TIME_LIMIT";
        }
    }

    /**
     * Đánh dấu bỏ cuộc trong state: HP=0, forfeited=true và ghi tick bị loại. Hàm chỉ đổi RAM;
     * RoomService/MatchService chịu trách nhiệm ghi dấu bền để phục hồi không hồi sinh người đã
     * rời.
     */
    public static void forfeit(GameState state, String userId) {
        PlayerState player = state.players.get(userId);
        if (player != null) {
            player.hp = 0;
            player.forfeited = true;
            player.eliminatedAtTick = state.tick;
        }
    }

    /**
     * Kiểm tra vị trí dự kiến không ra ngoài map, chồng tường hoặc chồng xe người chơi còn sống.
     * Chỉ đọc state hiện tại; nếu bị chặn thì bước di chuyển giữ tọa độ cũ.
     */
    private static boolean canMove(GameState state, PlayerState player, double x, double y) {
        double half = state.rules.tankHalf;
        if (!CombatGeometry.clear(state, x, y, half, false)) return false;
        return state.players.values().stream()
                .noneMatch(
                        other ->
                                other.hp > 0
                                        && !other.id.equals(player.id)
                                        && Math.abs(x - other.x) < 2 * half
                                        && Math.abs(y - other.y) < 2 * half);
    }

    /**
     * Sinh ý định cho bot Arena: định kỳ chọn hướng bằng PRNG và ưu tiên ngắm khi thẳng hàng với
     * đối thủ sống. Bot vẫn đi qua cùng luật di chuyển/bắn/cooldown như người; lịch nghĩ và hướng
     * bot nằm trong checkpoint.
     */
    private static PlayerInput botInput(GameState state, PlayerState bot) {
        if (state.tick >= bot.botThinkAt) {
            bot.botThinkAt = (int) state.tick + 12;
            bot.botDirection = CombatGeometry.randomDirection(state);
            for (PlayerState target : state.players.values())
                if (target.hp > 0 && !target.id.equals(bot.id)) {
                    if (Math.abs(bot.x - target.x) < state.rules.tankHalf) {
                        bot.botDirection = target.y < bot.y ? "UP" : "DOWN";
                        break;
                    }
                    if (Math.abs(bot.y - target.y) < state.rules.tankHalf) {
                        bot.botDirection = target.x < bot.x ? "LEFT" : "RIGHT";
                        break;
                    }
                }
        }
        return new PlayerInput(bot.botDirection, true);
    }
}
