package vn.edu.utc.tankbattle.business.model;

import java.util.List;

/** Luật/bản đồ có phiên bản, nằm trong checkpoint để restore giữ nguyên thông số của trận. */
public class GameRules {
    public int version;
    public int width;
    public int height;
    public int tile;
    public int tickMs;
    public int durationTicks;
    public int hp;
    public double tankHalf;
    public double tankSpeed;
    public double bulletSpeed;
    public double bulletRadius;
    public int fireCooldownTicks;
    public int bulletLifeTicks;
    public List<SpawnPoint> spawns;
    public List<WallState> walls;
}
