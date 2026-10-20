package vn.edu.utc.tankbattle.business.model;

/** Đạn có owner, hướng, tọa độ và số tick sống; toàn bộ phải được lưu trong checkpoint. */
public class BulletState {
    public int id;
    public int life;
    public String owner;
    public String direction;
    public double x;
    public double y;
    public int damage = 1;
    public boolean enemy;
}
