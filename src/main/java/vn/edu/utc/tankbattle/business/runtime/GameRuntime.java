package vn.edu.utc.tankbattle.business.runtime;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.business.model.PlayerInput;
import vn.edu.utc.tankbattle.common.constant.AppConstants.RuntimeMode;

/**
 * State tạm của một phòng. Truy cập state/input trong synchronized(runtime). Các cờ ghi DB dùng
 * AtomicBoolean để mỗi trận chỉ có một tác vụ cùng loại chờ ghi.
 */
public class GameRuntime {
    // Định danh phòng và state gameplay. State được chụp vào checkpoint; runtime không phải entity.
    public final long roomId;
    public final String roomCode;
    public final GameState state;
    // Ý định mới nhất và thời điểm nhận: tick loại input quá cũ để xe không chạy mãi khi mất keyup.
    public final Map<String, PlayerInput> inputs = new HashMap<>();
    public final Map<String, Long> lastInputAt = new HashMap<>();
    // Sequence chỉ có nghĩa trong kết nối hiện tại; handler loại lệnh gửi lặp/đến trễ.
    public final Map<String, Long> sequences = new HashMap<>();
    // Presence và xác nhận resume là dữ liệu RAM; reconnect phải cập nhật lại sau restart server.
    public final Map<String, Long> disconnectedAt = new HashMap<>();
    public final Set<String> resumeReady = new HashSet<>();
    // Tick và executor cùng dùng các cờ này để không xếp nhiều tác vụ ghi cùng loại cho một trận.
    public final AtomicBoolean checkpointQueued = new AtomicBoolean();
    public final AtomicBoolean finishQueued = new AtomicBoolean();
    // volatile giúp tick thấy kết quả executor; thao tác nhiều trường/state vẫn cần khóa runtime.
    public volatile RuntimeMode mode = RuntimeMode.RUNNING;
    public volatile long lastSavedAt = System.currentTimeMillis();
    public volatile long nextSaveAttemptAt;
    public volatile String abortReason;
    public volatile boolean pauseRequested;
    // Nhịp phát frame độc lập với nhịp tick và checkpoint, tránh gửi toàn bộ state mỗi 50ms.
    public long lastBroadcastAt;
    public volatile String storageMessage = "";
    // Hạn phục hồi là Instant bền trong DB; resume/restart không được tự gia hạn.
    public Instant recoveryDeadline;

    /**
     * Gắn một state đã tạo/khôi phục với phòng sở hữu và khởi bộ nhớ điều phối mới. State bền nằm
     * trong GameState; input, sequence, socket presence và cờ queue chỉ có hiệu lực trong process
     * hiện tại.
     */
    public GameRuntime(long roomId, String roomCode, GameState state) {
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.state = state;
    }
}
