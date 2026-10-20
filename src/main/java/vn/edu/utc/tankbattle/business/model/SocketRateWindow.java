package vn.edu.utc.tankbattle.business.model;

/** Bộ đếm trên mỗi socket; chỉ handler truy cập, bộ nhớ được giải phóng khi đóng kết nối. */
public class SocketRateWindow {
    private long started = System.nanoTime();
    private int count;

    public synchronized boolean allow() {
        long now = System.nanoTime();
        if (now - started >= 1_000_000_000L) {
            started = now;
            count = 0;
        }
        return ++count <= 60;
    }
}
