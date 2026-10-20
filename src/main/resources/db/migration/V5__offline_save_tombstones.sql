-- Giữ revision khi xóa slot để request từ thiết bị cũ không ghi đè slot đã tạo lại.
-- Xóa vẫn hủy payload; chỉ giữ tối đa ba hàng metadata/user.
ALTER TABLE offline_saves ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
