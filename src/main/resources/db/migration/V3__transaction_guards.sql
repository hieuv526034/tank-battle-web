-- COUNT + INSERT tạo phòng phải tuần tự để không vượt giới hạn khi nhiều request cùng lúc.
CREATE TABLE app_locks (
    name VARCHAR(40) PRIMARY KEY
);
INSERT INTO app_locks(name) VALUES('CREATE_ROOM');
