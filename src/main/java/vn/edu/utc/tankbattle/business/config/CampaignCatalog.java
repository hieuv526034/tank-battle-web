package vn.edu.utc.tankbattle.business.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.business.model.CampaignStage;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/** Nạp cửa từ JSON một lần, kiểm tra tham chiếu địch và chặn wave quá lớn. */
@Component
public class CampaignCatalog {
    private final List<CampaignStage> stages;

    /**
     * Đọc campaign-stages.json và kiểm tra ba cửa đánh số liên tiếp khi startup. Mỗi cửa phải có
     * thời lượng/số wave hợp lệ; mỗi wave giới hạn số địch và mọi code phải có trong
     * GameContentLoader. Fail sớm khi file sai giúp tránh mở trận rồi mới phát hiện không spawn
     * được địch.
     */
    public CampaignCatalog(ObjectMapper json, GameContentLoader content) {
        try (InputStream input =
                new ClassPathResource("static/campaign-stages.json").getInputStream()) {
            stages =
                    List.copyOf(json.readValue(input, new TypeReference<List<CampaignStage>>() {}));
        } catch (IOException invalid) {
            throw new IllegalStateException("Không đọc được campaign-stages.json", invalid);
        }
        if (stages.size() != 3) throw new IllegalStateException("Cần cấu hình đúng ba cửa.");
        for (int i = 0; i < stages.size(); i++) {
            CampaignStage stage = stages.get(i);
            if (stage.number() != i + 1
                    || stage.name() == null
                    || stage.durationTicks() < 200
                    || stage.durationTicks() > 36000
                    || stage.waves() == null
                    || stage.waves().isEmpty()
                    || stage.waves().size() > 10)
                throw new IllegalStateException("Cửa không hợp lệ.");
            for (List<String> wave : stage.waves()) {
                if (wave.isEmpty()
                        || wave.size() > 16
                        || wave.stream().anyMatch(code -> content.enemy(code).isEmpty()))
                    throw new IllegalStateException(
                            "Wave trống/quá lớn hoặc mã địch không tồn tại.");
            }
        }
    }

    /** Trả danh sách cửa đã validate cho catalog API; không đọc lại file mỗi request/tick. */
    public List<CampaignStage> stages() {
        return stages;
    }

    /**
     * Lấy định nghĩa cửa theo số thứ tự 1–3 khi tạo trận Campaign. Số cửa sai trả
     * GAME_INVALID_INPUT. Quyền mở khóa cửa thuộc MatchService, không được suy từ việc tìm thấy cấu
     * hình ở đây.
     */
    public CampaignStage stage(int number) {
        if (number < 1 || number > stages.size())
            throw BusinessException.of(ValidateCode.GAME_INVALID_INPUT);
        return stages.get(number - 1);
    }
}
