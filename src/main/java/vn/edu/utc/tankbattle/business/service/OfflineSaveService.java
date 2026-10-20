package vn.edu.utc.tankbattle.business.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.business.dto.request.OfflineSaveRequest;
import vn.edu.utc.tankbattle.business.dto.response.OfflineSaveResponse;
import vn.edu.utc.tankbattle.business.engine.StateCodec;
import vn.edu.utc.tankbattle.business.model.*;
import vn.edu.utc.tankbattle.business.repository.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/**
 * Đồng bộ cá nhân có revision. Không có đường gọi MatchService/RankingService từ bản lưu client.
 */
@Service
@Transactional(readOnly = true)
public class OfflineSaveService {
    private final OfflineSaveRepository saves;
    private final RoomRepository locks;
    private final StateCodec codec;

    /**
     * Nhận repository save, khóa user dùng chung và codec checkpoint. Lưu offline là dữ liệu cá
     * nhân tách khỏi MatchService/RankingService, nên điểm client tự sửa không trở thành thành tích
     * online.
     */
    public OfflineSaveService(OfflineSaveRepository saves, RoomRepository locks, StateCodec codec) {
        this.saves = saves;
        this.locks = locks;
        this.codec = codec;
    }

    /**
     * Đọc metadata tối đa ba slot chưa xóa của user, state trong response để null. Danh sách không
     * tải payload lớn; client chỉ GET slot cụ thể khi cần chơi tiếp.
     */
    public List<OfflineSaveResponse> list(long userId) {
        return saves.list(userId).stream().map(row -> response(row, false)).toList();
    }

    /**
     * Đọc đầy đủ slot thuộc user và giải mã checkpoint sau khi kiểm tra slot 1–3. Slot không có
     * hoặc đã đánh dấu xóa đều trả SAVE_NOT_FOUND; không đọc được bản lưu của user khác bằng cách
     * đổi slot.
     */
    public OfflineSaveResponse get(long userId, int slot) {
        validateSlot(slot);
        return response(
                saves.find(userId, slot)
                        .filter(row -> !row.deleted())
                        .orElseThrow(() -> BusinessException.of(ValidateCode.SAVE_NOT_FOUND)),
                true);
    }

    /**
     * Tạo/cập nhật bản lưu cá nhân có kiểm tra revision trong một transaction. Codec kiểm tra
     * payload trước khi khóa; sau đó khóa user để hai thiết bị không cùng tạo slot hoặc ghi đè
     * phiên bản nhau. Slot mới/đã xóa yêu cầu expectedRevision=0; slot đang có yêu cầu revision
     * đang giữ. UPDATE vẫn có điều kiện revision ở DB. V5 giữ mốc revision sau xóa, nên tạo lại
     * slot không cho request cũ từ thiết bị khác ghi đè.
     *
     * @param userId chủ bản lưu lấy từ JWT
     * @param slot vị trí lưu 1–3
     * @param request checkpoint client và revision đang biết
     * @return bản lưu đã commit với revision mới; xung đột trả SAVE_REVISION_CONFLICT
     */
    @Transactional
    public OfflineSaveResponse put(long userId, int slot, OfflineSaveRequest request) {
        validateSlot(slot);
        String payload;
        try {
            payload = codec.write(codec.read(codec.write(request.getState())));
        } catch (RuntimeException invalid) {
            throw BusinessException.of(ValidateCode.SAVE_INVALID);
        }
        // Khóa user bảo vệ cả INSERT lần đầu, không dùng upsert ghi đè revision của thiết bị khác.
        locks.lockUser(userId);
        OfflineSaveRow old = saves.find(userId, slot).orElse(null);
        long expected = old == null || old.deleted() ? 0 : old.revision();
        if (request.getExpectedRevision() != expected)
            throw BusinessException.of(ValidateCode.SAVE_REVISION_CONFLICT);
        if (old == null) saves.insert(userId, slot, payload);
        else if (saves.update(userId, slot, old.revision(), payload) != 1)
            throw BusinessException.of(ValidateCode.SAVE_REVISION_CONFLICT);
        return get(userId, slot);
    }

    /**
     * Xóa nội dung save khi revision khớp, cùng thứ tự khóa user như put. Repository hủy payload và
     * giữ metadata revision đã tăng như tombstone; GET/list không hiển thị slot này. Việc giữ
     * revision ngăn trường hợp xóa rồi tạo lại vô tình chấp nhận request cũ.
     */
    @Transactional
    public void delete(long userId, int slot, long revision) {
        validateSlot(slot);
        locks.lockUser(userId);
        if (saves.delete(userId, slot, revision) != 1)
            throw BusinessException.of(ValidateCode.SAVE_REVISION_CONFLICT);
    }

    /**
     * Giữ invariant ba slot ở service để caller nội bộ cũng bị kiểm tra như API. Slot ngoài 1–3 trả
     * SAVE_INVALID trước khi truy cập DB.
     */
    private void validateSlot(int slot) {
        if (slot < 1 || slot > 3) throw BusinessException.of(ValidateCode.SAVE_INVALID);
    }

    /**
     * Map projection save sang response; chỉ giải mã payload khi caller cần state. Nhánh list giữ
     * state=null để không tải/serialize checkpoint của cả ba slot.
     */
    private OfflineSaveResponse response(OfflineSaveRow row, boolean includeState) {
        return new OfflineSaveResponse(
                row.slot(),
                row.revision(),
                row.updatedAt(),
                includeState ? codec.read(row.payload()) : null);
    }
}
