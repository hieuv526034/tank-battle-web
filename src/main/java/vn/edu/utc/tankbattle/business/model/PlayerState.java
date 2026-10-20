package vn.edu.utc.tankbattle.business.model;

import vn.edu.utc.tankbattle.common.constant.AppConstants.ItemType;

/** State mô phỏng của một xe; không ánh xạ JPA và không chứa session/socket. */
public class PlayerState {
    public String id;
    public String name;
    public String direction = "UP";
    public String botDirection = "NONE";
    public double x;
    public double y;
    public int hp;
    public int cooldown;
    public int botThinkAt;
    public boolean bot;
    public boolean forfeited;

    /** Phân biệt xe chết ở tick cuối với xe đã bị loại trước đó khi xác định hòa. */
    public long eliminatedAtTick = -1;

    public int kills;
    public int score;
    public java.util.Map<ItemType, BuffState> buffs = new java.util.EnumMap<>(ItemType.class);

    public PlayerState() {}

    public PlayerState(String id, String name, int hp, double x, double y) {
        this.id = id;
        this.name = name;
        this.hp = hp;
        this.x = x;
        this.y = y;
    }
}
