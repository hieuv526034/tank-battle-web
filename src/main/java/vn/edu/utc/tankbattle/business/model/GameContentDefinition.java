package vn.edu.utc.tankbattle.business.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Catalog có phiên bản, chia sẻ được giữa server Java và client offline JavaScript. */
public record GameContentDefinition(
        @Min(value = 1, message = "{game.invalid_input}")
                @Max(value = 1, message = "{game.invalid_input}")
                int version,
        @NotEmpty(message = "{game.invalid_input}") List<@NotNull @Valid EnemyDefinition> enemies,
        @NotEmpty(message = "{game.invalid_input}") List<@NotNull @Valid ItemDefinition> items) {
    public GameContentDefinition {
        enemies = enemies == null ? null : List.copyOf(enemies);
        items = items == null ? null : List.copyOf(items);
    }
}
