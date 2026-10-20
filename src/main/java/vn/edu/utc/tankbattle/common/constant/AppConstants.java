package vn.edu.utc.tankbattle.common.constant;

/**
 * Hằng số và enum dùng chung của ứng dụng.
 *
 * <p>Chỉ chứa quy tắc ổn định. Thông số vận hành lấy từ application.yml; tốc độ/HP/bản đồ lấy từ
 * game-rules.json để Java và JavaScript dùng cùng dữ liệu.
 */
public final class AppConstants {
    public static final String DEFAULT_LANGUAGE = "vi";
    public static final java.util.List<String> SUPPORTED_LANGUAGES = java.util.List.of("vi", "en");
    public static final String SOCKET_IDENTITY = "tank.socket.identity";
    public static final String SOCKET_HTTP_SESSION = "tank.socket.httpSession";
    public static final String SOCKET_LOCALE = "tank.socket.locale";
    public static final int MIN_ROOM_PLAYERS = 2;
    public static final int MAX_ROOM_PLAYERS = 4;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_ROOM_LIST_SIZE = 40;
    public static final int SNAPSHOT_SCHEMA_VERSION = 2;
    public static final int MAX_BULLETS = 64;
    public static final int MAX_CHECKPOINT_CHARACTERS = 1_000_000;
    public static final int MAX_SOCKET_MESSAGE_BYTES = 2048;
    public static final int USERNAME_MIN_LENGTH = 4;
    public static final int USERNAME_MAX_LENGTH = 24;
    public static final int DISPLAY_NAME_MIN_LENGTH = 2;
    public static final int DISPLAY_NAME_MAX_LENGTH = 30;
    public static final int PASSWORD_MIN_LENGTH = 12;
    public static final int PASSWORD_MAX_LENGTH = 64;
    public static final int ROOM_NAME_MIN_LENGTH = 3;
    public static final int ROOM_NAME_MAX_LENGTH = 40;
    public static final int ROOM_CODE_LENGTH = 8;
    public static final int INPUT_STALE_MS = 500;
    public static final int WIN_POINTS = 3;
    public static final int DRAW_POINTS = 1;
    public static final int LOSS_POINTS = 0;
    public static final String USERNAME_PATTERN = "[a-zA-Z0-9_]{4,24}";
    public static final String CONFIG_CODE_PATTERN = "[A-Z][A-Z0-9_]{2,31}";
    public static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final String REFRESH_COOKIE_NAME = "TANK_REFRESH";
    public static final String REFRESH_COOKIE_PATH = "/api/auth";

    public enum UserRole {
        USER,
        ADMIN
    }

    public enum AuthProvider {
        GOOGLE
    }

    public enum Difficulty {
        EASY,
        NORMAL,
        HARD
    }

    public enum GameMode {
        ARENA,
        CAMPAIGN
    }

    public enum ItemType {
        REPAIR,
        DAMAGE_BOOST,
        SHIELD,
        SPEED,
        RAPID_FIRE
    }

    public enum EnemyMovement {
        GROUND,
        AIR
    }

    public enum EnemyBehavior {
        CHASE,
        PATROL,
        RANGED
    }

    /** Một bộ quy tắc text, tái sử dụng cho request và kiểm tra invariant ở service. */
    public enum TextKind {
        USERNAME(ValidateCode.USER_USERNAME_INVALID),
        DISPLAY_NAME(ValidateCode.USER_DISPLAY_NAME_INVALID),
        PASSWORD(ValidateCode.USER_PASSWORD_INVALID),
        ROOM_NAME(ValidateCode.ROOM_NAME_INVALID),
        ROOM_CODE(ValidateCode.ROOM_CODE_INVALID);

        private final ValidateCode validateCode;

        TextKind(ValidateCode validateCode) {
            this.validateCode = validateCode;
        }

        public ValidateCode getValidateCode() {
            return validateCode;
        }
    }

    private AppConstants() {}

    /**
     * Mã public ổn định theo tiền tố domain. Không đổi nghĩa/tái sử dụng mã đã phát hành.
     * messageKey trỏ vào bundle i18n; service không chứa thông báo gửi người dùng.
     */
    public enum ValidateCode {
        SYS_SUCCESS("SYS_000", "system.success", 200),
        SYS_INTERNAL_ERROR("SYS_001", "system.internal_error", 500),
        SYS_INVALID_REQUEST("SYS_002", "system.invalid_request", 400),
        SYS_MALFORMED_JSON("SYS_003", "system.malformed_json", 400),
        SYS_TOO_MANY_REQUESTS("SYS_004", "system.too_many_requests", 429),
        SYS_DATA_CONFLICT("SYS_005", "system.data_conflict", 409),
        SYS_SERVICE_UNAVAILABLE("SYS_006", "system.service_unavailable", 503),
        AUTH_LOGIN_REQUIRED("AUTH_001", "auth.login_required", 401),
        AUTH_INVALID_CREDENTIALS("AUTH_002", "auth.invalid_credentials", 401),
        AUTH_ACCESS_DENIED("AUTH_003", "auth.access_denied", 403),
        AUTH_CSRF_INVALID("AUTH_004", "auth.csrf_invalid", 403),
        AUTH_INVALID_TOKEN("AUTH_005", "auth.invalid_token", 401),
        AUTH_INVALID_REFRESH("AUTH_006", "auth.invalid_refresh", 401),
        AUTH_REFRESH_REUSED("AUTH_007", "auth.refresh_reused", 401),
        AUTH_OAUTH_FAILED("AUTH_008", "auth.oauth_failed", 401),
        USER_NOT_FOUND("USER_001", "user.not_found", 404),
        USER_USERNAME_INVALID("USER_002", "user.username.invalid", 400),
        USER_DISPLAY_NAME_INVALID("USER_003", "user.display_name.invalid", 400),
        USER_PASSWORD_INVALID("USER_004", "user.password.invalid", 400),
        USER_PASSWORD_MISMATCH("USER_005", "user.password.mismatch", 400),
        USER_USERNAME_TAKEN("USER_006", "user.username.taken", 409),
        ROOM_NOT_FOUND("ROOM_001", "room.not_found", 404),
        ROOM_NAME_INVALID("ROOM_002", "room.name.invalid", 400),
        ROOM_CAPACITY_INVALID("ROOM_003", "room.capacity.invalid", 400),
        ROOM_FULL("ROOM_004", "room.full", 409),
        ROOM_NOT_WAITING("ROOM_005", "room.not_waiting", 409),
        ROOM_NOT_MEMBER("ROOM_006", "room.not_member", 403),
        ROOM_ALREADY_JOINED("ROOM_007", "room.already_joined", 409),
        ROOM_NOT_HOST("ROOM_008", "room.not_host", 403),
        ROOM_NOT_READY("ROOM_009", "room.not_ready", 409),
        ROOM_CHANGED("ROOM_010", "room.changed", 409),
        ROOM_SERVER_FULL("ROOM_011", "room.server_full", 503),
        ROOM_NOT_CONNECTED("ROOM_012", "room.not_connected", 409),
        ROOM_CODE_INVALID("ROOM_013", "room.code.invalid", 400),
        MATCH_NOT_FOUND("MATCH_001", "match.not_found", 404),
        MATCH_NOT_PARTICIPANT("MATCH_002", "match.not_participant", 403),
        MATCH_NOT_RECOVERING("MATCH_003", "match.not_recovering", 409),
        MATCH_CANNOT_RESUME("MATCH_004", "match.cannot_resume", 409),
        MATCH_RECOVERY_EXPIRED("MATCH_005", "match.recovery_expired", 409),
        MATCH_NOT_ACTIVE("MATCH_006", "match.not_active", 409),
        GAME_INVALID_INPUT("GAME_001", "game.invalid_input", 400),
        GAME_STAGE_LOCKED("GAME_002", "game.stage_locked", 409),
        SAVE_NOT_FOUND("SAVE_001", "save.not_found", 404),
        SAVE_REVISION_CONFLICT("SAVE_002", "save.revision_conflict", 409),
        SAVE_INVALID("SAVE_003", "save.invalid", 400),
        PAGE_INVALID("PAGE_001", "page.invalid", 400);

        private final String code;
        private final String messageKey;
        private final int httpStatus;

        ValidateCode(String code, String messageKey, int httpStatus) {
            this.code = code;
            this.messageKey = messageKey;
            this.httpStatus = httpStatus;
        }

        public String getCode() {
            return code;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public static ValidateCode fromMessageKey(String key) {
            return java.util.Arrays.stream(values())
                    .filter(value -> value.messageKey.equals(key))
                    .findFirst()
                    .orElse(SYS_INVALID_REQUEST);
        }
    }

    /** Quyền chủ phòng được kiểm tra theo hostUserId, không phải role toàn hệ thống. */
    public enum RoomStatus {
        WAITING,
        RUNNING,
        RECOVERING,
        CLOSED
    }

    public enum MatchStatus {
        RUNNING,
        RECOVERING,
        FINISHED,
        ABORTED
    }

    public enum PlayerOutcome {
        WIN,
        LOSS,
        DRAW
    }

    /** ENDING: engine đã dừng nhưng kết quả chưa commit thành công. */
    public enum GamePhase {
        RUNNING,
        ENDING,
        FINISHED
    }

    public enum RuntimeMode {
        RUNNING,
        RECOVERING,
        SAVING,
        ENDING,
        FINISHED,
        ABORTED
    }

    public enum SocketType {
        SUBSCRIBE,
        INPUT,
        SYNC,
        LOBBY,
        GAME,
        FINISHED,
        ERROR
    }

    /** Vector đơn vị để tính chuyển động theo thời gian, không phụ thuộc FPS vẽ. */
    public enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0),
        NONE(0, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public int dx() {
            return dx;
        }

        public int dy() {
            return dy;
        }
    }
}
