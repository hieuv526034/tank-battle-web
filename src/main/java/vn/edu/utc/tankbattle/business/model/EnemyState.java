package vn.edu.utc.tankbattle.business.model;

/** Cá thể địch: giữ cả thông số đã nhân độ khó để restore không phụ thuộc cấu hình mới. */
public class EnemyState {
    public String id;
    public EnemyDefinition definition;
    public double x;
    public double y;
    public String direction = "DOWN";
    public int hp;
    public int cooldown;
    public int contactCooldown;
    public long thinkAt;
}
