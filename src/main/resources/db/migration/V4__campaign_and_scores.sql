-- Giá trị mặc định bảo toàn mọi trận Arena cũ. Điểm chỉ do engine server kết toán.
ALTER TABLE matches ADD COLUMN game_mode VARCHAR(16) NOT NULL DEFAULT 'ARENA' CHECK(game_mode IN ('ARENA','CAMPAIGN'));
ALTER TABLE matches ADD COLUMN difficulty VARCHAR(16) NOT NULL DEFAULT 'NORMAL' CHECK(difficulty IN ('EASY','NORMAL','HARD'));
ALTER TABLE matches ADD COLUMN stage_number INTEGER NOT NULL DEFAULT 1 CHECK(stage_number BETWEEN 1 AND 3);
ALTER TABLE match_players ADD COLUMN kills INTEGER NOT NULL DEFAULT 0 CHECK(kills>=0);
ALTER TABLE match_players ADD COLUMN score INTEGER NOT NULL DEFAULT 0 CHECK(score>=0);
CREATE INDEX idx_matches_mode_status ON matches(game_mode,status,difficulty,stage_number);
-- Save offline là dữ liệu riêng, không được dùng làm kết quả online/ranking.
CREATE TABLE offline_saves (
    user_id BIGINT NOT NULL REFERENCES users(id),
    slot INTEGER NOT NULL CHECK(slot BETWEEN 1 AND 3),
    revision BIGINT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id,slot)
);
