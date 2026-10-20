package vn.edu.utc.tankbattle.business.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.edu.utc.tankbattle.common.base.request.BaseRequest;
import vn.edu.utc.tankbattle.common.constant.AppConstants.*;

/** Lựa chọn trước trận; tham số chiến đấu/điểm do server lấy từ catalog. */
@Schema(
        description =
                "Chọn Arena 2–4 người hoặc Campaign 1–4 người, không nhận state/điểm từ client.")
public class StartMatchRequest extends BaseRequest {
    @NotNull(message = "{game.invalid_input}")
    @Schema(description = "ARENA đối kháng, CAMPAIGN hợp tác.", defaultValue = "ARENA")
    private GameMode mode = GameMode.ARENA;

    @NotNull(message = "{game.invalid_input}")
    @Schema(
            description = "Độ khó AI, ảnh hưởng HP/tốc độ/cooldown/điểm địch.",
            defaultValue = "NORMAL")
    private Difficulty difficulty = Difficulty.NORMAL;

    @Min(value = 1, message = "{game.invalid_input}")
    @Max(value = 3, message = "{game.invalid_input}")
    @Schema(
            description = "Cửa 1–3; mở khóa cửa kế sau chiến thắng cùng độ khó. Arena chỉ dùng 1.",
            example = "1")
    private int stage = 1;

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }
}
