package vn.edu.utc.tankbattle.business.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.edu.utc.tankbattle.business.dto.response.CampaignRankResponse;
import vn.edu.utc.tankbattle.business.dto.response.RankResponse;
import vn.edu.utc.tankbattle.business.repository.RankingRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants;

/** Quy tắc phân trang/thứ hạng; SQL tổng hợp nằm trong RankingRepository. */
@Service
public class RankingService {
    private final RankingRepository rankings;

    /**
     * Nhận repository tổng hợp điểm; service chuyển tham số phân trang còn SQL window/aggregate nằm
     * ở repository.
     */
    public RankingService(RankingRepository rankings) {
        this.rankings = rankings;
    }

    /**
     * Đọc trang Arena với kích thước mặc định cho caller nội bộ. Hạng được tính trên toàn bảng
     * trước LIMIT/OFFSET; điểm lấy từ trận FINISHED.
     */
    public List<RankResponse> page(int page) {
        return rankings.page(
                AppConstants.DEFAULT_PAGE_SIZE, Math.max(0, page) * AppConstants.DEFAULT_PAGE_SIZE);
    }

    /**
     * Đọc một trang xếp hạng Arena sau khi controller đã validate page/size. Offset kiểm tra tràn
     * số; repository xếp theo điểm, thắng, thua và userId để phân trang ổn định.
     */
    public List<RankResponse> page(int page, int size) {
        return rankings.page(size, Math.multiplyExact(page, size));
    }

    /**
     * Đọc hạng Arena toàn bảng của chính user; rỗng khi chưa có trận FINISHED. Repository tính
     * window rank trước khi lọc user để không biến mọi tài khoản thành hạng 1.
     */
    public Optional<RankResponse> personal(long userId) {
        return rankings.personal(userId);
    }

    /**
     * Đọc hạng Campaign theo độ khó: số cửa đã thắng rồi tổng điểm tốt nhất từng cửa. Mỗi cửa chỉ
     * lấy best score của trận WIN/FINISHED, nên chơi lại không cộng lặp các lần chạy.
     */
    public List<CampaignRankResponse> campaign(String difficulty, int page, int size) {
        return rankings.campaign(difficulty, size, Math.multiplyExact(page, size));
    }

    /**
     * Liệt kê các cửa user đã WIN online ở độ khó chỉ định để UI hiển thị tiến độ. Khi start trận,
     * MatchService vẫn kiểm tra unlock trong transaction, không tin danh sách client đang giữ.
     */
    public List<Integer> clearedStages(long userId, String difficulty) {
        return rankings.clearedStages(userId, difficulty);
    }

    /**
     * Đọc hạng Campaign toàn bảng của một user tại độ khó đã chọn. Rỗng khi chưa thắng cửa nào;
     * dùng cùng công thức với danh sách ranking, kể cả khi user ở ngoài trang đang xem.
     */
    public Optional<CampaignRankResponse> campaignPersonal(long userId, String difficulty) {
        return rankings.campaignPersonal(userId, difficulty);
    }
}
