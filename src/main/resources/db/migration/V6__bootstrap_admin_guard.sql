-- Tuần tự hóa bootstrap admin giữa các tiến trình; không lưu mật khẩu trong migration.
INSERT INTO app_locks(name) VALUES ('BOOTSTRAP_ADMIN');
