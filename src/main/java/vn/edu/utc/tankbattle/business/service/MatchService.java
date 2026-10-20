package vn.edu.utc.tankbattle.business.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.business.dto.response.MatchResponse;
import vn.edu.utc.tankbattle.business.dto.response.MemberResponse;
import vn.edu.utc.tankbattle.business.dto.response.ResultResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.engine.GameEngine;
import vn.edu.utc.tankbattle.business.engine.StateCodec;
import vn.edu.utc.tankbattle.business.model.GameState;
import vn.edu.utc.tankbattle.business.model.MatchPlayerResult;
import vn.edu.utc.tankbattle.business.model.PlayerState;
import vn.edu.utc.tankbattle.business.repository.MatchRepository;
import vn.edu.utc.tankbattle.business.repository.RoomRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/**
 * Quản lý phần bền của trận: khởi tạo roster/checkpoint, lưu tiến trình, phục hồi và kết toán.
 * GameCoordinator gọi service từ luồng điều phối/executor; REST đọc lịch sử qua projection riêng.
 * Repository giữ SQL/batch, engine tính luật bằng RAM; service không chạy mỗi lần xe di chuyển.
 *
 * <p>Các thao tác đổi trạng thái dùng transaction và terminal guard để checkpoint/retry không ghi
 * đè trận đã kết thúc hoặc tạo điểm nhiều lần. Thứ tự khóa phòng trước trận thống nhất với
 * RoomService.
 */
@Service
public class MatchService {
    private final MatchRepository matches;
    private final RoomRepository roomData;
    private final RoomService rooms;
    private final StateCodec codec;

    /**
     * Nhận kho trận/phòng, RoomService cho khóa/invariant và StateCodec để ghi checkpoint. Service
     * giữ ranh giới transaction; engine tính kết quả trong RAM và repository thực thi SQL/batch.
     */
    public MatchService(
            MatchRepository matches, RoomRepository roomData, RoomService rooms, StateCodec codec) {
        this.matches = matches;
        this.roomData = roomData;
        this.rooms = rooms;
        this.codec = codec;
    }

    /**
     * Đọc projection nội bộ gồm metadata và payload checkpoint, ném MATCH_NOT_FOUND nếu không có.
     * Payload chỉ dành cho runtime/khôi phục; API công khai dùng metadata và kiểm tra
     * requireParticipant ở entry point.
     */
    public MatchResponse get(String id) {
        return matches.find(id)
                .orElseThrow(() -> BusinessException.of(ValidateCode.MATCH_NOT_FOUND));
    }

    /**
     * Lấy tất cả trận RUNNING/RECOVERING kèm checkpoint cho quá trình khôi phục startup. Đây là một
     * truy vấn theo lô, không đọc từng trận từ vòng lặp mô phỏng.
     */
    public List<MatchResponse> unfinished() {
        return matches.unfinished();
    }

    /**
     * Đọc metadata nhẹ, bỏ cột payload khỏi query để xem chi tiết/lịch sử không tải JSON checkpoint
     * lớn. Quyền tham gia được kiểm tra riêng trước khi trả DTO cho client.
     */
    public MatchResponse metadata(String id) {
        return matches.metadata(id)
                .orElseThrow(() -> BusinessException.of(ValidateCode.MATCH_NOT_FOUND));
    }

    /**
     * Lấy danh sách user đã bỏ cuộc cho nhiều matchId bằng một query IN. Coordinator dùng kết quả
     * nhóm theo matchId lúc startup để đối chiếu checkpoint mà không query từng người.
     */
    public Map<String, List<String>> allForfeits(List<String> ids) {
        return matches.allForfeits(ids);
    }

    /**
     * Chuẩn bị phục hồi nhiều trận bằng transaction: khóa room ID tăng dần, batch chuyển match/room
     * sang RECOVERING và reset ready. Trận chưa có deadline nhận mốc mới theo recoveryMs; deadline
     * đã có được giữ nguyên qua mọi lần restart. Chỉ gọi sau khi đã đọc danh sách unfinished ở
     * startup.
     */
    @Transactional
    public void prepareRecovery(List<MatchResponse> recoverable, long recoveryMs) {
        if (!recoverable.isEmpty()) {
            roomData.lockRooms(
                    recoverable.stream().map(MatchResponse::roomId).distinct().sorted().toList());
            matches.prepareRecovery(
                    recoverable,
                    Instant.now()
                            .plusMillis(recoveryMs)
                            .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        }
    }

    /**
     * Đọc lịch sử của user với kích thước trang mặc định cho caller nội bộ. Repository chỉ trả
     * metadata, giữ checkpoint payload ngoài danh sách.
     */
    public List<MatchResponse> history(long userId, int page) {
        return matches.history(
                userId,
                AppConstants.DEFAULT_PAGE_SIZE,
                Math.max(0, page) * AppConstants.DEFAULT_PAGE_SIZE);
    }

    /**
     * Đọc một trang lịch sử theo membership lịch sử match_players. Controller validate page/size
     * trước khi gọi; offset được tính có kiểm tra tràn số và SQL sắp xếp ổn định theo thời gian/ID.
     */
    public List<MatchResponse> history(long userId, int page, int size) {
        return matches.history(userId, size, Math.multiplyExact(page, size));
    }

    /**
     * Kiểm tra user từng thuộc roster của trận, kể cả sau khi đã rời phòng. Ném
     * MATCH_NOT_PARTICIPANT để chặn truy cập lịch sử/state/kết quả của người khác; quyền dựa trên
     * DB, không dựa vào tên hiển thị.
     */
    public void requireParticipant(String id, long userId) {
        if (!matches.participates(id, userId))
            throw BusinessException.of(ValidateCode.MATCH_NOT_PARTICIPANT);
    }

    /**
     * Đọc kết quả từng ghế từ dữ liệu DB đã lưu; HP/outcome có thể null trước kết toán. Caller phải
     * kiểm tra requireParticipant; hàm không nhận điểm/kết quả do client tự khai.
     */
    public List<ResultResponse> results(String id) {
        return matches.results(id);
    }

    /**
     * Tạo metadata, roster và snapshot đầu của trận trong một transaction. Khóa lại room, kiểm tra
     * chủ phòng/WAITING, số người theo chế độ, all ready và roster khớp bản Coordinator vừa đọc.
     * Campaign kiểm tra mở khóa cho cả nhóm bằng một query; sau đó batch INSERT người chơi và
     * chuyển room RUNNING.
     *
     * @param expected roster trước khi tạo state, dùng phát hiện thay đổi xen ngang
     * @param hostId người yêu cầu start lấy từ JWT
     * @param state state do server tạo; Coordinator chỉ đăng ký runtime sau khi hàm commit
     */
    @Transactional
    public void begin(RoomResponse expected, long hostId, GameState state) {
        RoomResponse room = rooms.lock(expected.code());
        if (room.hostUserId() != hostId) throw BusinessException.of(ValidateCode.ROOM_NOT_HOST);
        if (!"WAITING".equals(room.status()))
            throw BusinessException.of(ValidateCode.ROOM_NOT_WAITING);
        if (room.members().size() < (state.mode == AppConstants.GameMode.ARENA ? 2 : 1)
                || room.members().stream().anyMatch(member -> !member.ready())) {
            throw BusinessException.of(ValidateCode.ROOM_NOT_READY);
        }
        if (!room.members().equals(expected.members()))
            throw BusinessException.of(ValidateCode.ROOM_CHANGED);
        if (!matches.stageUnlocked(
                room.members().stream().map(MemberResponse::userId).toList(),
                state.difficulty.name(),
                state.stage == null ? 1 : state.stage.number()))
            throw BusinessException.of(ValidateCode.GAME_STAGE_LOCKED);
        matches.insert(state, room.id());
        matches.insertPlayers(state.matchId, room.members());
        matches.insertSnapshot(state.matchId, state.schemaVersion, state.tick, codec.write(state));
        roomData.setStatus(room.id(), "RUNNING");
    }

    /**
     * Ghi snapshot định kỳ trên executor persistence, không chạy trong tick. Khóa match và chỉ ghi
     * khi RUNNING; repository dùng tick đơn điệu để bản lưu cũ không đè bản mới. Các cờ forfeited
     * trong snapshot được ghi theo lô cùng transaction để khôi phục đọc được quyết định bỏ cuộc đã
     * lưu.
     */
    @Transactional
    public void checkpoint(GameState state) {
        if ("RUNNING".equals(matches.lockStatus(state.matchId))) {
            matches.saveForfeits(
                    state.matchId,
                    state.players.values().stream()
                            .filter(player -> player.forfeited)
                            .map(player -> Long.valueOf(player.id))
                            .toList());
            matches.checkpoint(state.matchId, state.tick, codec.write(state));
        }
    }

    /**
     * Đối chiếu danh sách forfeited_at ở DB vào state RAM trước khôi phục/kết toán. Explicit leave
     * có thể đã commit sau checkpoint cuối; bước này đặt lại HP/cờ forfeited để người đó không sống
     * lại từ snapshot cũ.
     */
    public void applyDurableForfeits(GameState state) {
        matches.forfeits(state.matchId).forEach(id -> GameEngine.forfeit(state, id));
    }

    /**
     * Kết toán khi engine đã xác định kết thúc, trong transaction khóa room rồi match. Terminal
     * guard FINISHED/ABORTED làm retry hoặc hai tác vụ finish không cộng kết quả lần nữa. Đối chiếu
     * bỏ cuộc, tính outcome, batch ghi HP/kills/score, lưu snapshot FINISHED và metadata rồi trả
     * room về lobby.
     *
     * @param state bản chụp do server tạo; client không được gọi kết toán bằng state tự khai
     */
    @Transactional
    public void finish(GameState state) {
        MatchResponse metadata = get(state.matchId);
        rooms.lock(metadata.roomCode());
        String status = matches.lockStatus(state.matchId);
        if ("FINISHED".equals(status) || "ABORTED".equals(status)) return;
        applyDurableForfeits(state);
        matches.saveForfeits(
                state.matchId,
                state.players.values().stream()
                        .filter(player -> player.forfeited)
                        .map(player -> Long.valueOf(player.id))
                        .toList());
        List<PlayerState> alive =
                state.players.values().stream().filter(player -> player.hp > 0).toList();
        String winner =
                state.mode == AppConstants.GameMode.ARENA && alive.size() == 1
                        ? alive.getFirst().id
                        : null;
        List<MatchPlayerResult> results = new ArrayList<>();
        for (PlayerState player : state.players.values()) {
            String outcome = outcome(state, player, winner);
            results.add(
                    new MatchPlayerResult(
                            Long.parseLong(player.id),
                            outcome,
                            player.hp,
                            player.kills,
                            player.score));
        }
        matches.updateResults(state.matchId, results);
        state.phase = "FINISHED";
        matches.saveFinalSnapshot(state.matchId, state.tick, codec.write(state));
        matches.finish(
                state.matchId, winner == null ? null : Long.valueOf(winner), state.endReason);
        finishRoom(metadata.roomId());
    }

    /**
     * Ánh xạ tình trạng cuối của một người sang WIN/DRAW/LOSS. Bỏ cuộc luôn LOSS; Campaign chỉ WIN
     * nếu còn sống khi clear cửa. Arena chỉ hòa các xe chết cùng tick cuối ở MUTUAL_ELIMINATION
     * hoặc còn sống khi hết giờ. Xe bị loại ở tick trước vẫn LOSS, giúp cùng chết không biến mọi
     * người trong trận thành hòa.
     */
    private String outcome(GameState state, PlayerState player, String winner) {
        if (player.forfeited) return "LOSS";
        if (state.mode == AppConstants.GameMode.CAMPAIGN)
            return player.hp > 0 && "CAMPAIGN_CLEARED".equals(state.endReason) ? "WIN" : "LOSS";
        if ("MUTUAL_ELIMINATION".equals(state.endReason) && player.eliminatedAtTick == state.tick)
            return "DRAW";
        if (player.hp <= 0) return "LOSS";
        if (winner == null) return "DRAW";
        return player.id.equals(winner) ? "WIN" : "LOSS";
    }

    /**
     * Hủy trận mà không tính ranking, dùng khi khôi phục thất bại/quá hạn/bị hủy. Khóa room rồi
     * match, bỏ qua nếu đã terminal, ghi ABORTED và đối soát lobby trong cùng transaction.
     *
     * @param reason mã lý do nội bộ để lưu metadata, không phải exception text gửi client
     */
    @Transactional
    public void abort(String id, String reason) {
        MatchResponse metadata = get(id);
        rooms.lock(metadata.roomCode());
        String status = matches.lockStatus(id);
        if ("FINISHED".equals(status) || "ABORTED".equals(status)) return;
        matches.abort(id, reason);
        finishRoom(metadata.roomId());
    }

    /**
     * Chuyển room về WAITING rồi đối soát roster sau khi trận đã được kết toán/hủy. Chạy trong
     * transaction đã khóa room của finish/abort; nếu mọi người đã rời thì reconcile chuyển thành
     * CLOSED.
     */
    private void finishRoom(long roomId) {
        roomData.setStatus(roomId, "WAITING");
        rooms.reconcile(roomId);
    }

    /**
     * Chuyển một trận đang hoạt động sang RECOVERING khi DB đã có checkpoint cần giữ. Khóa room rồi
     * match, giữ deadline cũ nếu có, reset ready và trả deadline để runtime đồng bộ đúng mốc đã
     * dùng. Thời điểm được chuẩn hóa microsecond để JDBC/H2/PostgreSQL lưu cùng độ chính xác.
     */
    @Transactional
    public Instant recovering(String id, long recoveryMs) {
        MatchResponse match = get(id);
        rooms.lock(match.roomCode());
        String status = matches.lockStatus(id);
        if (!java.util.Set.of("RUNNING", "RECOVERING").contains(status))
            throw BusinessException.of(ValidateCode.MATCH_NOT_ACTIVE);
        Instant deadline =
                match.recoveryDeadline() == null
                        ? Instant.now()
                                .plusMillis(recoveryMs)
                                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                        : match.recoveryDeadline();
        matches.recovering(id, deadline);
        roomData.setStatus(match.roomId(), "RECOVERING");
        roomData.resetReady(match.roomId());
        return deadline;
    }

    /**
     * Tạm dừng khi mọi người còn sống đóng trình duyệt: lưu snapshot và đặt RECOVERING trong cùng
     * transaction. Khóa room rồi match; nếu vẫn RUNNING thì lưu snapshot/forfeit trước khi đổi
     * trạng thái. Retry trên RECOVERING giữ nguyên deadline.
     *
     * @param state snapshot đóng băng từ runtime
     * @param recoveryMs thời gian tối đa chờ nhóm quay lại
     * @return deadline UTC để Coordinator phát lại cho các kết nối
     */
    @Transactional
    public Instant pause(GameState state, long recoveryMs) {
        MatchResponse match = get(state.matchId);
        rooms.lock(match.roomCode());
        String status = matches.lockStatus(state.matchId);
        if (!java.util.Set.of("RUNNING", "RECOVERING").contains(status))
            throw BusinessException.of(ValidateCode.MATCH_NOT_ACTIVE);
        if ("RUNNING".equals(status)) {
            matches.saveForfeits(
                    state.matchId,
                    state.players.values().stream()
                            .filter(player -> player.forfeited)
                            .map(player -> Long.valueOf(player.id))
                            .toList());
            matches.checkpoint(state.matchId, state.tick, codec.write(state));
        }
        Instant deadline =
                match.recoveryDeadline() == null
                        ? Instant.now()
                                .plusMillis(recoveryMs)
                                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                        : match.recoveryDeadline();
        matches.recovering(state.matchId, deadline);
        roomData.setStatus(match.roomId(), "RECOVERING");
        roomData.resetReady(match.roomId());
        return deadline;
    }

    /**
     * Chuyển DB từ RECOVERING về RUNNING sau khi Coordinator đã gom đủ người sẵn sàng. Khóa room
     * rồi match; UPDATE có điều kiện deadline còn hạn để tránh request tới muộn hoặc resume lặp.
     * Chỉ sau commit Coordinator mới tiếp tục mô phỏng runtime.
     */
    @Transactional
    public void resume(String id) {
        MatchResponse match = get(id);
        rooms.lock(match.roomCode());
        if (!"RECOVERING".equals(matches.lockStatus(id)))
            throw BusinessException.of(ValidateCode.MATCH_NOT_RECOVERING);
        if (matches.resume(id) != 1) throw BusinessException.of(ValidateCode.MATCH_NOT_RECOVERING);
        roomData.setStatus(match.roomId(), "RUNNING");
    }
}
