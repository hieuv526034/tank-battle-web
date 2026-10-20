package vn.edu.utc.tankbattle.business.model;

import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.EnemyBehavior;
import vn.edu.utc.tankbattle.common.constant.AppConstants.EnemyMovement;

/**
 * Định nghĩa một loại địch; không chứa vị trí/HP hiện tại của một cá thể. Tốc độ tính pixel/giây,
 * cooldown tính tick; killScore chỉ cộng khi địch chết một lần.
 */
public record EnemyDefinition(
        @NotBlank(message = "{game.invalid_input}")
                @Pattern(
                        regexp = AppConstants.CONFIG_CODE_PATTERN,
                        message = "{game.invalid_input}")
                String code,
        @NotBlank(message = "{game.invalid_input}")
                @Size(max = 40, message = "{game.invalid_input}")
                String displayName,
        @NotBlank(message = "{game.invalid_input}")
                @Pattern(regexp = "[a-z][a-z0-9_-]{1,31}", message = "{game.invalid_input}")
                String renderKey,
        @NotNull(message = "{game.invalid_input}") EnemyMovement movement,
        @NotNull(message = "{game.invalid_input}") EnemyBehavior behavior,
        @Min(value = 1, message = "{game.invalid_input}")
                @Max(value = 100, message = "{game.invalid_input}")
                int health,
        @DecimalMin(value = "1", message = "{game.invalid_input}")
                @DecimalMax(value = "500", message = "{game.invalid_input}")
                double speed,
        @Min(value = 0, message = "{game.invalid_input}")
                @Max(value = 20, message = "{game.invalid_input}")
                int contactDamage,
        @Min(value = 0, message = "{game.invalid_input}")
                @Max(value = 20, message = "{game.invalid_input}")
                int projectileDamage,
        @Min(value = 0, message = "{game.invalid_input}")
                @Max(value = 1200, message = "{game.invalid_input}")
                int fireCooldownTicks,
        @DecimalMin(value = "2", message = "{game.invalid_input}")
                @DecimalMax(value = "32", message = "{game.invalid_input}")
                double hitboxRadius,
        @Min(value = 1, message = "{game.invalid_input}")
                @Max(value = 100000, message = "{game.invalid_input}")
                int killScore) {}
