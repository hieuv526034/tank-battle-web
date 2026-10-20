package vn.edu.utc.tankbattle.business.service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.repository.MatchRepository;
import vn.edu.utc.tankbattle.business.repository.RoomRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidationRules;

/**
 * Quản lý vòng đời phòng chờ và ghế thành viên: tạo, tham gia, ready, rời và dọn người mất kết nối.
 * Controller/Coordinator gọi service để kiểm luật; RoomRepository giữ SQL và projection roster.
 *
 * <p>Thay roster của phòng có sẵn phải khóa room trước user; tạo phòng dùng guard trước user.
 * Transaction ghi hoàn tất trước khi caller phát lobby qua socket, tránh công bố dữ liệu rollback.
 */
@Service
public class RoomService {
    private final RoomRepository rooms;
    private final MatchRepository matches;
    private final SecureRandom random = new SecureRandom();
    private final int maxRooms;

    /**
     * Nhận repository phòng/trận và giới hạn số phòng từ cấu hình. RoomRepository giữ SQL và khóa;
     * MatchRepository ghi dấu bỏ cuộc khi thành viên rời trận.
     */
    public RoomService(
            RoomRepository rooms,
            MatchRepository matches,
            @Value("${tank.max-rooms}") int maxRooms) {
        this.rooms = rooms;
        this.matches = matches;
        this.maxRooms = maxRooms;
    }

    /**
     * Chuẩn hóa, validate mã mời rồi đọc phòng kèm roster hiện tại. Không khóa hàng và không tự
     * kiểm tra quyền thành viên; entry point dùng requireMember trước khi trả thông tin riêng của
     * lobby.
     *
     * @throws BusinessException ROOM_CODE_INVALID hoặc ROOM_NOT_FOUND
     */
    public RoomResponse byCode(String code) {
        String normalizedCode = normalizeCode(code);
        ValidationRules.require(TextKind.ROOM_CODE, normalizedCode);
        return rooms.byCode(normalizedCode, false)
                .orElseThrow(() -> BusinessException.of(ValidateCode.ROOM_NOT_FOUND));
    }

    /**
     * Đọc phòng kèm roster bằng khóa số nội bộ. Dùng sau khi đã xác định roomId từ
     * membership/runtime; trả ROOM_NOT_FOUND nếu phòng không tồn tại.
     */
    public RoomResponse byId(long id) {
        return rooms.byId(id).orElseThrow(() -> BusinessException.of(ValidateCode.ROOM_NOT_FOUND));
    }

    /**
     * Đọc phòng và lấy khóa hàng FOR UPDATE để giữ roster ổn định trong thao tác ghi. Caller phải
     * mở transaction; khóa được giữ tới commit/rollback. Với phòng có sẵn, lấy khóa room trước user
     * hoặc match để thống nhất thứ tự chờ.
     */
    public RoomResponse lock(String code) {
        return rooms.byCode(normalizeCode(code), true)
                .orElseThrow(() -> BusinessException.of(ValidateCode.ROOM_NOT_FOUND));
    }

    /**
     * Lấy tối đa số phòng đang mở theo cấu hình truy vấn repository. Repository đọc header và toàn
     * bộ member theo lô, nên số query không tăng theo số phòng.
     */
    public List<RoomResponse> list() {
        return rooms.list();
    }

    /**
     * Tìm membership duy nhất của user và trả phòng tương ứng; rỗng khi chưa tham gia. UserId do
     * controller/handshake lấy từ danh tính xác thực, không nhận tùy ý từ request.
     */
    public Optional<RoomResponse> current(long userId) {
        return rooms.currentRoomId(userId).map(this::byId);
    }

    /**
     * Kiểm tra user có trong roster đã đọc, ném ROOM_NOT_MEMBER khi không có. Chỉ duyệt DTO trong
     * RAM; helper này không đọc hoặc thay đổi DB.
     */
    public void requireMember(RoomResponse room, long userId) {
        if (room.members().stream().noneMatch(member -> member.userId() == userId)) {
            throw BusinessException.of(ValidateCode.ROOM_NOT_MEMBER);
        }
    }

    /**
     * Tạo phòng và ghế chủ phòng trong một transaction. Khóa guard CREATE_ROOM rồi user để việc đếm
     * maxRooms và kiểm tra một user/một phòng không bị vượt khi request đồng thời. Sau validate
     * tên/capacity, sinh mã chưa trùng, INSERT phòng + membership và trả roster đã lưu.
     *
     * @param userId người tạo từ JWT
     * @param name tên phòng được chuẩn hóa trước khi lưu
     * @param capacity sức chứa 2–4, không phải số người bắt buộc phải có ngay lúc tạo
     * @return phòng mới; controller phát lobby sau khi transaction đã commit
     */
    @Transactional
    public RoomResponse create(long userId, String name, int capacity) {
        String normalizedName = TextNormalizer.name(name);
        ValidationRules.require(TextKind.ROOM_NAME, normalizedName);
        rooms.lockCreationGuard();
        rooms.lockUser(userId);
        if (current(userId).isPresent())
            throw BusinessException.of(ValidateCode.ROOM_ALREADY_JOINED);
        if (capacity < AppConstants.MIN_ROOM_PLAYERS || capacity > AppConstants.MAX_ROOM_PLAYERS) {
            throw BusinessException.of(ValidateCode.ROOM_CAPACITY_INVALID);
        }
        if (rooms.activeCount() >= maxRooms)
            throw BusinessException.of(ValidateCode.ROOM_SERVER_FULL);
        String code;
        do {
            code = randomCode();
        } while (rooms.codeExists(code));
        long roomId = rooms.insert(code, normalizedName, userId, capacity);
        rooms.addMember(userId, roomId, 0);
        return byId(roomId);
    }

    /**
     * Cấp ghế trống cho user sau khi khóa room rồi user trong cùng transaction. Join lặp cùng phòng
     * trả lại membership hiện có; đang ở phòng khác, phòng đầy hoặc không WAITING thì trả mã lỗi
     * riêng. Sau khi thêm thành viên, reset ready để chủ phòng không dùng trạng thái sẵn sàng của
     * roster cũ.
     */
    @Transactional
    public RoomResponse join(long userId, String code) {
        String normalizedCode = normalizeCode(code);
        ValidationRules.require(TextKind.ROOM_CODE, normalizedCode);
        RoomResponse room = lock(normalizedCode);
        rooms.lockUser(userId);
        Optional<RoomResponse> existing = current(userId);
        if (existing.isPresent()) {
            if (existing.get().id() == room.id()) return existing.get();
            throw BusinessException.of(ValidateCode.ROOM_ALREADY_JOINED);
        }
        if (!"WAITING".equals(room.status()))
            throw BusinessException.of(ValidateCode.ROOM_NOT_WAITING);
        if (room.members().size() >= room.capacity())
            throw BusinessException.of(ValidateCode.ROOM_FULL);
        Set<Integer> used = new HashSet<>();
        room.members().forEach(member -> used.add(member.seat()));
        int seat = 0;
        while (used.contains(seat)) seat++;
        rooms.addMember(userId, room.id(), seat);
        rooms.resetReady(room.id());
        return byId(room.id());
    }

    /**
     * Đổi cờ sẵn sàng của chính thành viên khi phòng WAITING. Khóa room rồi user, kiểm tra
     * membership trước UPDATE; caller phát lobby sau commit để các máy thấy trạng thái đã lưu.
     */
    @Transactional
    public void ready(long userId, String code, boolean ready) {
        RoomResponse room = lock(code);
        rooms.lockUser(userId);
        requireMember(room, userId);
        if (!"WAITING".equals(room.status()))
            throw BusinessException.of(ValidateCode.ROOM_NOT_WAITING);
        rooms.setReady(userId, ready);
    }

    /**
     * Rời phòng hoặc bỏ cuộc bằng transaction giữ thứ tự room rồi user. Ghi forfeited_at vào trận
     * còn hoạt động trước khi xóa membership để phục hồi không hồi sinh người đã chủ động rời. Sau
     * đó đối soát chủ phòng/ready; phòng rỗng chuyển CLOSED, lịch sử match_players vẫn giữ.
     */
    @Transactional
    public void leave(long userId, String code) {
        RoomResponse room = lock(code);
        rooms.lockUser(userId);
        requireMember(room, userId);
        matches.markForfeited(userId, room.id());
        rooms.removeMember(userId, room.id());
        rooms.reconcile(room.id());
    }

    /**
     * Đối soát roster sau thay đổi: chuyển quyền chủ phòng nếu cần, reset ready hoặc đóng phòng
     * rỗng. Hàm tham gia transaction/khóa room của caller, ví dụ leave hoặc MatchService kết toán
     * trận.
     */
    public void reconcile(long roomId) {
        rooms.reconcile(roomId);
    }

    /**
     * Dọn một lô thành viên mất kết nối quá hạn ở lobby WAITING. Repository khóa các room theo ID
     * tăng dần rồi xóa/reset/batch cập nhật chủ phòng; tránh gọi leave riêng cho từng user.
     */
    @Transactional
    public void evictDisconnected(Set<Long> userIds) {
        rooms.evictDisconnected(userIds);
    }

    /**
     * Chuẩn hóa mã phòng bằng TextNormalizer để cùng một mã được tìm nhất quán ở API và runtime.
     */
    public static String normalizeCode(String code) {
        return TextNormalizer.roomCode(code);
    }

    /**
     * Sinh mã mời theo alphabet loại ký tự dễ nhầm trong AppConstants bằng SecureRandom. create
     * chịu trách nhiệm kiểm tra mã đã tồn tại trước INSERT, trong transaction có guard tạo phòng.
     */
    private String randomCode() {
        StringBuilder code = new StringBuilder();
        String alphabet = AppConstants.ROOM_CODE_ALPHABET;
        for (int index = 0; index < AppConstants.ROOM_CODE_LENGTH; index++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }
}
