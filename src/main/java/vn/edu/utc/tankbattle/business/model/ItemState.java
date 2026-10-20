package vn.edu.utc.tankbattle.business.model;

/** Vật phẩm trên bản đồ, tự biến mất theo tick; definition được giữ trong checkpoint. */
public class ItemState {
    public int id;
    public ItemDefinition definition;
    public double x;
    public double y;
    public long expiresAtTick;
}
