package vn.edu.utc.tankbattle.business.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.edu.utc.tankbattle.business.model.EnemyDefinition;
import vn.edu.utc.tankbattle.business.model.GameContentDefinition;
import vn.edu.utc.tankbattle.business.model.ItemDefinition;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ItemType;

/**
 * Đọc/validate catalog một lần khi khởi động, không đọc file/query DB trong từng tick. Engine chụp
 * catalog vào state lúc mở trận để bản lưu giữ nguyên luật khi cấu hình mới thay đổi.
 */
@Component
public class GameContentLoader {
    private final GameContentDefinition definition;
    private final Map<String, EnemyDefinition> enemies;

    /**
     * Nạp game-content.json một lần khi tạo bean và validate thông số/kiểu dữ liệu bằng Bean
     * Validation. Kiểm tra thêm code không trùng, địch bắn có cooldown và item instant/thời hạn hợp
     * lệ; lỗi file làm dừng startup. Tạo map tra địch theo code để CampaignCatalog kiểm tra wave
     * nhanh trong RAM.
     */
    public GameContentLoader(ObjectMapper json, Validator validator) {
        try (InputStream input =
                new ClassPathResource("static/game-content.json").getInputStream()) {
            definition = json.readValue(input, GameContentDefinition.class);
        } catch (IOException invalidFile) {
            throw new IllegalStateException(
                    "Không đọc được cấu hình game-content.json.", invalidFile);
        }
        if (!validator.validate(definition).isEmpty()) {
            throw new IllegalStateException("Thông số game-content.json không hợp lệ.");
        }
        Set<String> codes = new HashSet<String>();
        for (EnemyDefinition enemy : definition.enemies()) {
            if (!codes.add(enemy.code())
                    || (enemy.projectileDamage() > 0 && enemy.fireCooldownTicks() == 0)) {
                throw new IllegalStateException("Mã địch trùng hoặc cooldown bắn không hợp lệ.");
            }
        }
        for (ItemDefinition item : definition.items()) {
            boolean instantEffect = item.effect() == ItemType.REPAIR;
            if (!codes.add(item.code()) || (instantEffect != (item.durationTicks() == 0))) {
                throw new IllegalStateException("Mã item trùng hoặc thời hạn item không hợp lệ.");
            }
        }
        enemies =
                Map.copyOf(
                        definition.enemies().stream()
                                .collect(
                                        Collectors.toMap(
                                                EnemyDefinition::code, Function.identity())));
    }

    /**
     * Trả catalog đã nạp và kiểm tra để API hiển thị hoặc engine chụp vào state khi start. Trận
     * đang chơi giữ catalog trong checkpoint nên cấu hình mới không đổi luật giữa trận.
     */
    public GameContentDefinition definition() {
        return definition;
    }

    /**
     * Tra định nghĩa loại địch theo code trong map RAM đã tạo lúc startup. CampaignCatalog dùng kết
     * quả để từ chối wave tham chiếu mã không tồn tại.
     */
    public Optional<EnemyDefinition> enemy(String code) {
        return Optional.ofNullable(enemies.get(code));
    }
}
