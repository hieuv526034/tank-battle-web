package vn.edu.utc.tankbattle.business.model;

import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ItemType;

/**
 * Cấu hình item. amount là lượng hồi/tăng theo effect; durationTicks=0 cho hiệu ứng tức thì. Các
 * buff có thời hạn dùng tick mô phỏng để không trôi thời gian khi dừng/khôi phục.
 */
public record ItemDefinition(
        @NotBlank(message = "{game.invalid_input}")
                @Pattern(
                        regexp = AppConstants.CONFIG_CODE_PATTERN,
                        message = "{game.invalid_input}")
                String code,
        @NotBlank(message = "{game.invalid_input}")
                @Size(max = 40, message = "{game.invalid_input}")
                String displayName,
        @NotNull(message = "{game.invalid_input}") ItemType effect,
        @Min(value = 1, message = "{game.invalid_input}")
                @Max(value = 100, message = "{game.invalid_input}")
                int amount,
        @Min(value = 0, message = "{game.invalid_input}")
                @Max(value = 3600, message = "{game.invalid_input}")
                int durationTicks) {}
