# Độ khó, cửa, địch và item đã nối vào backend

Backend đã dùng catalog trong GameEngine/CampaignEngine, không còn chỉ khai báo enum. UI/JavaScript/offline browser chưa làm. Luồng chi tiết và test ở [tài liệu 20](20-luong-backend-va-test.md).

## Chế độ và mở khóa

StartMatchRequest chọn ARENA hoặc CAMPAIGN, EASY/NORMAL/HARD, stage 1–3. Mặc định ARENA/NORMAL/1. Arena cần 2–4 người, Campaign hợp tác 1–4; mọi thành viên ready và có socket.

Ba cửa cấu hình tại src/main/resources/static/campaign-stages.json. Mỗi wave liệt kê mã địch của game-content.json. Loader kiểm tra số cửa, thứ tự, thời lượng, số wave/địch và tham chiếu mã khi startup.

Cửa 1 mở sẵn. Cửa kế yêu cầu **tất cả thành viên** đã WIN cửa liền trước ở cùng độ khó, bằng một query theo lô. GAME_002 khi chưa mở. Không dùng thành tích offline để mở cửa online.

## Độ khó

| Độ khó | HP địch                        | Tốc độ | Cooldown bắn | Điểm diệt địch |
| ------ | ------------------------------ | ------ | ------------ | -------------- |
| EASY   | ceil(base × 0.75), tối thiểu 1 | × 0.8  | base / 0.8   | base           |
| NORMAL | base                           | base   | base         | base           |
| HARD   | ceil(base × 1.5)               | × 1.15 | base / 1.15  | base × 2       |

HARD còn giảm khoảng cách giữa các lần AI quyết định. Đây là thông số khởi đầu, cần cân bằng bằng chơi thử khi UI có. Arena dùng cùng luật đối kháng; độ khó chỉ ảnh hưởng AI Campaign hiện tại.

## Wave và kết thúc

Spawn ở vị trí trong map, tránh tường cho địch GROUND và tránh gần người chơi. AIR bỏ qua tường khi di chuyển. Hết wave mới spawn wave tiếp. Một wave có item hỗ trợ; diệt địch có xác suất rơi item. Hết mọi wave là CAMPAIGN_CLEARED; không còn người sống hoặc hết giờ là thất bại.

Mỗi người còn sống, không bỏ cuộc lúc clear nhận WIN. Điểm theo địch đã diệt lưu riêng từng người. Ranking Campaign xét số cửa đã thắng rồi tổng best score mỗi cửa theo độ khó. Không trộn ranking Arena.

## Buff và khôi phục

REPAIR hồi đến maxHP; DAMAGE_BOOST tăng damage đạn mới; SHIELD giảm damage mỗi tick/contact; SPEED tăng tốc; RAPID_FIRE giảm cooldown có chặn tối thiểu. Cùng loại làm mới thời hạn, không cộng dồn.

Buff dùng expiresAtTick; nghỉ/restart không trôi thời gian. Checkpoint schema 2 giữ seed, AI think/cooldown, wave, enemy, item, bullet, score và buff cùng cấu hình đã dùng. GameEngineTest so sánh mô phỏng liên tục với restore rồi chạy tiếp.
