# Cấu hình địch, vật phẩm và điểm

Đã tích hợp vào engine Java và checkpoint schema 2. Các file cấu hình ở src/main/resources/static; API GET /api/game/catalog trả cùng nội dung cho UI tương lai.

## Loại địch

game-content.json có code, displayName, renderKey, movement, behavior, health, speed, contactDamage, projectileDamage, fireCooldownTicks, hitboxRadius và killScore.

| Code            | Hình gợi ý | HP/điểm base | Cách đánh                          |
| --------------- | ---------- | ------------ | ---------------------------------- |
| TANK_BASIC      | Xe tăng    | 3 / 30       | GROUND, RANGED                     |
| BIRD_SCOUT      | Chim       | 2 / 20       | AIR, RANGED, sát thương tiếp xúc   |
| BUTTERFLY_SWARM | Bướm       | 1 / 10       | AIR, PATROL, sát thương tiếp xúc   |
| BEETLE_ARMORED  | Bọ giáp    | 5 / 50       | GROUND, CHASE, sát thương tiếp xúc |

RenderKey để frontend chọn hình, backend không phụ thuộc sprite. Đổi hình chim thành động vật khác bằng catalog/renderKey; muốn thêm loại thì thêm code hợp lệ và tham chiếu trong wave. Chưa có màn hình admin sửa catalog; hiện sửa JSON và restart.

Loader kiểm tra code không trùng, giới hạn thông số, địch có damage đạn phải có cooldown, item instant/thời hạn hợp lệ. CampaignCatalog kiểm tra mã wave tồn tại. Mỗi trận giữ cấu hình riêng trong checkpoint, không đổi giữa trận khi deploy catalog mới.

AI dùng mục tiêu gần nhất hoặc tuần tra PRNG có seed. Địch đất tránh tường, địch bay đi qua tường; đạn vẫn bị tường chặn. AI đơn giản dành đồ án, chưa có tìm đường phức tạp.

## Item

Năm item: hồi máu +1, damage +1 trong 200 tick, khiên 1 trong 200 tick, tốc độ +25% trong 160 tick, bắn nhanh 30% trong 160 tick. 20 tick/giây. Không cộng dồn cùng loại; nhặt lại làm mới hạn. Hồi máu không vượt maxHP, không hồi sinh người chết.

Diệt địch có xác suất 50% rơi loại item ngẫu nhiên; seed và thứ tự state ổn định sau restore. Tối đa 32 item trên bản đồ, tồn tại 600 tick. Nhặt trong bán kính xe +8; mỗi item chỉ một người nhận. Cân bằng xác suất/thông số bằng thử game sau này.

## Điểm và bảng xếp hạng

PlayerState có kills/score; đạn gắn damage tại lúc bắn. Gom damage trong tick rồi loại địch, ghi một kill/killScore đúng một lần. HARD nhân đôi killScore. Arena diệt xe có 100 điểm thành tích nhưng hạng Arena vẫn theo WIN 3/DRAW 1/LOSS 0.

V4 thêm kills/score vào match_players và mode/difficulty/stage vào matches. MatchService kết toán theo batch trong transaction FINISHED, retry không increment điểm. Ranking Campaign chỉ lấy score tốt nhất mỗi cửa trong trận WIN đã FINISHED; thứ hạng tính trước phân trang.

OfflineSaveService chỉ lưu dữ liệu cá nhân trong offline_saves. Tự sửa score trong bản lưu không đưa vào bảng xếp hạng online.

Xem [luồng và test](20-luong-backend-va-test.md) để theo dõi cụ thể các lớp.
