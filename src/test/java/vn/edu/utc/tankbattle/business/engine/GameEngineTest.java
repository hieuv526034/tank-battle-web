package vn.edu.utc.tankbattle.business.engine;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.utc.tankbattle.business.config.*;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants.*;

/** Test luật độc lập Spring/DB: kết quả phải tái hiện được từ checkpoint và input. */
class GameEngineTest {
    private final ObjectMapper json = new ObjectMapper();
    private final StateCodec codec = new StateCodec(json);
    private GameContentLoader content;
    private CampaignCatalog campaign;

    @BeforeEach
    void setup() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            content = new GameContentLoader(json, factory.getValidator());
            campaign = new CampaignCatalog(json, content);
        }
    }

    private GameState arena() {
        GameState state =
                GameEngine.create(
                        UUID.randomUUID().toString(),
                        codec.rules(),
                        List.of(
                                new PlayerState("1", "A", 3, 0, 0),
                                new PlayerState("2", "B", 3, 0, 0)));
        state.rules.walls = List.of();
        return state;
    }

    private GameState campaign(Difficulty difficulty) {
        return GameEngine.create(
                UUID.randomUUID().toString(),
                codec.rules(),
                List.of(new PlayerState("1", "A", 3, 0, 0)),
                content.definition(),
                GameMode.CAMPAIGN,
                difficulty,
                campaign.stage(1));
    }

    @Test
    void restoreKeepsDeterministicAiBulletsBuffsItemsAndSeed() {
        GameState original = campaign(Difficulty.HARD);
        original.players.get("1").buffs.put(ItemType.DAMAGE_BOOST, new BuffState(1, 400));
        for (int tick = 0; tick < 80; tick++)
            GameEngine.step(original, Map.of("1", new PlayerInput("RIGHT", true)));
        GameState restored = codec.read(codec.write(original));
        for (int tick = 0; tick < 120; tick++) {
            Map<String, PlayerInput> inputs =
                    Map.of("1", new PlayerInput(tick % 2 == 0 ? "DOWN" : "LEFT", true));
            GameEngine.step(original, inputs);
            GameEngine.step(restored, inputs);
        }
        assertThat(codec.write(restored)).isEqualTo(codec.write(original));
        assertThat(restored.mode).isEqualTo(GameMode.CAMPAIGN);
    }

    @Test
    void enemyDeathCreditsExactlyOnceEvenWhenTwoBulletsHitSameTick() {
        GameState state = campaign(Difficulty.NORMAL);
        state.rules.walls = List.of();
        state.enemies.clear();
        state.items.clear();
        EnemyState enemy = new EnemyState();
        enemy.id = "E100";
        enemy.definition = content.enemy("TANK_BASIC").orElseThrow();
        enemy.hp = 3;
        enemy.x = 300;
        enemy.y = 200;
        enemy.cooldown = 100;
        state.enemies.put(enemy.id, enemy);
        GameEngine.fire(state, "1", 260, 200, "RIGHT", 13, 3, false);
        GameEngine.fire(state, "1", 260, 200, "RIGHT", 13, 3, false);
        GameEngine.step(state, Map.of());
        assertThat(state.players.get("1").kills).isEqualTo(1);
        assertThat(state.players.get("1").score).isEqualTo(30);
        assertThat(state.enemies).doesNotContainKey("E100");
        GameEngine.step(state, Map.of());
        assertThat(state.players.get("1").score).isEqualTo(30);
    }

    @Test
    void itemsHealToCapAndRefreshBuffWithoutStackingAndExpireByTick() {
        GameState state = arena();
        state.content = content.definition();
        PlayerState player = state.players.get("1");
        player.hp = 2;
        CampaignEngine.drop(state, content.definition().items().get(0), player.x, player.y);
        CampaignEngine.collect(state);
        assertThat(player.hp).isEqualTo(3);
        ItemDefinition buff = new ItemDefinition("ITEM_TEST", "Test", ItemType.DAMAGE_BOOST, 2, 5);
        CampaignEngine.drop(state, buff, player.x, player.y);
        CampaignEngine.collect(state);
        state.tick = 2;
        CampaignEngine.drop(state, buff, player.x, player.y);
        CampaignEngine.collect(state);
        assertThat(GameEngine.buff(player, ItemType.DAMAGE_BOOST)).isEqualTo(2);
        assertThat(player.buffs.get(ItemType.DAMAGE_BOOST).expiresAtTick()).isEqualTo(7);
        state.tick = 6;
        GameEngine.step(state, Map.of());
        assertThat(GameEngine.buff(player, ItemType.DAMAGE_BOOST)).isZero();
        assertThat(state.items).isEmpty();
    }

    @Test
    void shieldReducesDamageAndSpeedRapidFireAffectServerSimulation() {
        GameState state = arena();
        PlayerState player = state.players.get("1");
        player.buffs.put(ItemType.SHIELD, new BuffState(1, 100));
        GameEngine.damage(state, player, 2);
        assertThat(player.hp).isEqualTo(2);
        player.buffs.put(ItemType.SPEED, new BuffState(50, 100));
        player.buffs.put(ItemType.RAPID_FIRE, new BuffState(50, 100));
        double before = player.x;
        GameEngine.step(state, Map.of("1", new PlayerInput("RIGHT", true)));
        assertThat(player.x - before).isCloseTo(9.6, within(0.001));
        assertThat(player.cooldown).isEqualTo(5);
    }

    @Test
    void difficultyScalesEnemiesAndWaveVictoryIsSeparateFromArenaSurvivor() {
        GameState easy = campaign(Difficulty.EASY), hard = campaign(Difficulty.HARD);
        EnemyState easyEnemy = easy.enemies.values().iterator().next(),
                hardEnemy = hard.enemies.values().iterator().next();
        assertThat(hardEnemy.definition.speed()).isGreaterThan(easyEnemy.definition.speed());
        assertThat(hardEnemy.definition.health()).isGreaterThan(easyEnemy.definition.health());
        GameEngine.evaluate(easy);
        assertThat(easy.phase).isEqualTo("RUNNING");
        easy.enemies.clear();
        GameEngine.evaluate(easy);
        assertThat(easy.wave).isEqualTo(1);
        assertThat(easy.enemies).isNotEmpty();
        easy.enemies.clear();
        GameEngine.evaluate(easy);
        assertThat(easy.endReason).isEqualTo("CAMPAIGN_CLEARED");
        assertThat(easy.phase).isEqualTo("ENDING");
    }

    @Test
    void simultaneousEliminationDoesNotInventWinner() {
        GameState state = arena();
        PlayerState first = state.players.get("1"), second = state.players.get("2");
        first.x = 100;
        first.y = 100;
        second.x = 150;
        second.y = 100;
        first.hp = 1;
        second.hp = 1;
        GameEngine.fire(state, "1", second.x - 30, 100, "RIGHT", 13, 1, false);
        GameEngine.fire(state, "2", first.x + 30, 100, "LEFT", 13, 1, false);
        GameEngine.step(state, Map.of());
        assertThat(first.hp).isZero();
        assertThat(second.hp).isZero();
        assertThat(state.endReason).isEqualTo("MUTUAL_ELIMINATION");
    }

    @Test
    void continuousCollisionStopsFastBulletAtWallBeforePlayer() {
        GameState state = arena();
        PlayerState first = state.players.get("1"), second = state.players.get("2");
        first.x = 100;
        first.y = 100;
        second.x = 250;
        second.y = 100;
        WallState wall = new WallState();
        wall.x = 180;
        wall.y = 50;
        wall.w = 5;
        wall.h = 100;
        state.rules.walls = List.of(wall);
        state.rules.bulletSpeed = 5000;
        GameEngine.step(state, Map.of("1", new PlayerInput("RIGHT", true)));
        assertThat(second.hp).isEqualTo(3);
        assertThat(state.bullets).isEmpty();
    }

    @Test
    void legacySchemaMigratesAndUnknownSchemaAndInvalidHealthAreRejected() {
        GameState state = arena();
        state.schemaVersion = 1;
        GameState migrated = codec.read(codec.write(state));
        assertThat(migrated.schemaVersion).isEqualTo(2);
        state.schemaVersion = 999;
        assertThatThrownBy(() -> codec.read(codec.write(state)))
                .isInstanceOf(IllegalArgumentException.class);
        state.schemaVersion = 2;
        state.players.get("1").hp = 100;
        assertThatThrownBy(() -> codec.read(codec.write(state)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forfeitAndTimeLimitSurviveCheckpoint() {
        GameState state = arena();
        GameEngine.forfeit(state, "1");
        GameState restored = codec.copy(state);
        GameEngine.evaluate(restored);
        assertThat(restored.players.get("1").forfeited).isTrue();
        assertThat(restored.endReason).isEqualTo("LAST_SURVIVOR");
        GameState timeout = campaign(Difficulty.NORMAL);
        timeout.remainingTicks = 1;
        GameEngine.step(timeout, Map.of());
        assertThat(timeout.endReason).isEqualTo("TIME_LIMIT");
    }
}
