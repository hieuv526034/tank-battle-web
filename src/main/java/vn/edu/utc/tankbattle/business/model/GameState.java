package vn.edu.utc.tankbattle.business.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import vn.edu.utc.tankbattle.common.constant.AppConstants.Difficulty;
import vn.edu.utc.tankbattle.common.constant.AppConstants.GameMode;

/** Checkpoint thuần dữ liệu; tên trường JSON giữ nguyên khi tách model để không làm mất bản lưu. */
public class GameState {
    public int schemaVersion = 2;
    public GameMode mode = GameMode.ARENA;
    public Difficulty difficulty = Difficulty.NORMAL;
    public GameContentDefinition content;
    public CampaignStage stage;
    public int wave;
    public int nextEnemyId = 1;
    public int nextItemId = 1;
    public Map<String, EnemyState> enemies = new TreeMap<>();
    public List<ItemState> items = new ArrayList<>();
    public String matchId;
    public GameRules rules;
    public long tick = 0;
    public long seed = 20261020;
    public int remainingTicks;
    public int nextBulletId = 1;
    public String phase = "RUNNING";
    public String endReason;
    public Map<String, PlayerState> players = new TreeMap<>();
    public List<BulletState> bullets = new ArrayList<>();
}
