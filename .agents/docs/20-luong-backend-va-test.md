# Luồng backend và nghiệm thu trước UI

Đọc cùng [API](11-swagger-va-api.md), [STATUS](STATUS.md) và [Neon production](19-neon-production.md). Giao diện/Canvas/engine JS/IndexedDB/Service Worker đã có trong [tài liệu 25](25-giao-dien-va-offline.md); chưa nghiệm thu gameplay browser xuyên suốt.

## 1. Cách đọc source

Gốc `vn.edu.utc.tankbattle` chỉ có `auth`, `common`, `business`. Business dùng các tầng chung, không chia mỗi tính năng thành một bộ thư mục riêng.

- Controller nhận request, lấy userId từ JWT, gọi service rồi tạo ApiResponse.
- Service giữ transaction/invariant, không chứa SQL hoặc lớp DTO/model lồng.
- Repository giữ SQL/projection/khóa/batch; mapper chuyển row sang DTO.
- Engine xử lý state trong RAM; runtime điều phối tick và queue lưu; websocket gửi/nhận message.
- Common giữ base, constants/enums, validator, normalize, lỗi/i18n, event và identity.
- Auth giữ tài khoản, JWT/Google, security, ticket handshake. Business nhận SocketIdentity chung, không import auth.

## 2. Đăng nhập và mở kết nối

1. GET /api/auth/csrf nhận cookie XSRF-TOKEN và data.token.
2. Register rồi login với cookie + X-XSRF-TOKEN. Access JWT nằm trong data; refresh cookie HttpOnly.
3. Dùng Authorization: Bearer TOKEN cho API nghiệp vụ riêng. Khách có thể GET /api/rankings và /api/rankings/campaign; route hạng/tiến độ cá nhân vẫn cần JWT. Không gửi userId/HP/điểm do client tự khai.
4. Tạo/vào phòng trước. POST /api/auth/socket-ticket cần Bearer **và** CSRF, cấp vé ngẫu nhiên dùng một lần, sống tối đa 30 giây.
5. Mở /ws/game?ticket=OPAQUE_TICKET. Không đưa JWT vào URL. Origin phải trùng host/scheme/port; web session cũng được handshake hỗ trợ.
6. Handler kiểm tra membership, bind socket mới nhất, đóng socket cũ và phát LOBBY.
7. Socket gắn hạn access token; UI phải phối hợp refresh, lấy vé mới và reconnect trước hết hạn. Sequence mới được reset khi kết nối lại. Logout/replay thu hồi sid đóng socket sau commit.
8. Message tối đa 2048 byte, tối đa 60 message/giây/socket. Input chỉ gồm type/matchId/sequence/direction/fire; không cho client ghi state.

Vé là bí mật ngắn hạn: không bật access log ghi query string của /ws/game. Lỗi handshake đi qua SecurityErrorWriter; lỗi message là SocketEvent ERROR chứa ApiResponse code/key/locale. Socket bị thay thế không được xóa presence của socket mới.

## 3. Phòng và bắt đầu trận

Create: guard CREATE_ROOM → khóa user → kiểm tra đang ở phòng/maxRooms → insert room + membership cùng transaction.

Join: khóa room → user → kiểm tra membership/capacity/trạng thái → thêm ghế còn trống → reset ready. PK user_id ngăn một người ở hai phòng, unique(room_id,seat) bảo vệ ghế. Join lặp cùng phòng trả lại phòng đó.

Ready chỉ đổi bản thân khi WAITING. Chủ phòng rời thì chuyển cho thành viên tham gia sớm nhất; rỗng thì CLOSED. Rời giữa trận ghi forfeited_at trước khi xóa membership, lịch sử vẫn giữ.

Start chọn ARENA/CAMPAIGN, EASY/NORMAL/HARD và stage. Arena cần 2–4 người, Campaign 1–4; mọi người ready và kết nối socket. Service khóa lại room, so roster, kiểm tra chủ phòng/trạng thái/unlock, tạo match + players + snapshot rồi commit. Coordinator chỉ tạo runtime sau commit.

## 4. Tick, địch và vật phẩm

Mỗi step đúng 50ms, độc lập FPS, đồng hồ hệ thống và DB. Thứ tự chính: hết hạn buff → input/move/fire → AI/contact → đạn → nhặt item → đánh giá kết thúc. Đạn xét giao cắt cả đoạn bay; gom damage để cho phép cùng bị loại một tick.

Campaign dùng ba cửa trong campaign-stages.json; mỗi cửa gồm nhiều wave tham chiếu game-content.json. AIR đi xuyên tường khi di chuyển; GROUND tránh tường. CHASE/RANGED chọn người sống gần nhất; PATROL dùng PRNG lưu seed. Đây là AI đơn giản, chưa phải thuật toán tìm đường A\*.

Độ khó nhân HP/tốc độ/cooldown và điểm địch; HARD nhân đôi điểm. Địch chết chỉ ghi một kill/killScore, kể cả nhiều đạn trúng cùng tick. Người chơi Campaign không bắn đồng đội.

Item nhặt tự động trong bán kính, một người nhận theo thứ tự ID ổn định. REPAIR không vượt maxHP; DAMAGE_BOOST tăng damage đạn mới; SHIELD giảm damage được gom mỗi tick/contact; SPEED theo phần trăm; RAPID_FIRE giảm cooldown có chặn tối thiểu. Cùng loại buff làm mới thời hạn và thay amount, không cộng dồn. Tick hết hạn được lưu nên pause không làm mất thời gian buff. Item rơi giới hạn 32, tự hết hạn sau 600 tick.

Trong Arena, điểm diệt xe là 100/kill; ranking vẫn dùng thắng/hòa/thua. Vật phẩm phát sinh theo wave/diệt địch ở Campaign.

## 5. Checkpoint và khôi phục

GameState schema 2 chứa rules, catalog, stage/wave, seed, tick, HP/vị trí/hướng/cooldown, score/kills/buff, bullets/enemies/items và bộ đếm ID. Codec nâng Arena schema 1 sang 2, từ chối phiên bản không hỗ trợ. Không đổi tên JSON tùy ý.

Checkpoint mặc định 2 giây, tick đơn điệu, chạy qua một executor queue tối đa 64. Không có repo call trong tick. Khi lưu chậm quá ngưỡng, mô phỏng tạm dừng; kết quả chỉ công bố chính thức sau commit.

- Một người mất kết nối, người khác còn chơi: có 30 giây reconnect; hết hạn xử bỏ cuộc và lưu bền qua checkpoint/kết quả.
- Tất cả người còn sống mất kết nối: dừng mô phỏng, lưu snapshot + chuyển RECOVERING trong một transaction.
- Startup: lấy unfinished/forfeit theo lô, khóa các room ID tăng dần, chuyển RECOVERING và nạp lại checkpoint. Giữ matchId và deadline đã có; restart không gia hạn.
- Người còn sống, còn membership, có socket xác nhận resume-ready. Chỉ chạy tiếp khi đủ người và chưa quá hạn (mặc định 10 phút).
- Quá hạn hoặc thành viên chọn cancel: ABORTED, không cộng xếp hạng.
- Snapshot đã ENDING: hoàn tất kết toán idempotent; không yêu cầu người chơi xác nhận lại.

Crash có thể mất phần từ checkpoint cuối tới lúc crash (mặc định khoảng 2 giây nếu storage khỏe), không hứa không mất từng input. Khóa runtime và DB có phạm vi riêng; chỉ vận hành một instance game.

## 6. Kết toán và bảng xếp hạng

GET /api/rankings và GET /api/rankings/campaign cho khách đọc danh sách theo P0. /me và /campaign/me/progress vẫn yêu cầu JWT; không cho phép toàn bộ /api/rankings/**. BusinessFlowTest kiểm cả HTTP và security trong OpenAPI.

Khóa room → match, kiểm tra terminal. Ghi toàn bộ outcome/HP/kills/score theo batch, snapshot FINISHED, metadata và trạng thái room cùng transaction. Retry thấy FINISHED/ABORTED thì dừng, không cộng thêm.

Arena: sống cuối WIN; timeout nhiều xe còn sống thì những xe đó DRAW; xe chết cùng tick cuối trong MUTUAL_ELIMINATION DRAW, xe chết trước/bỏ cuộc LOSS. WIN 3, DRAW 1, LOSS 0. Hạng DENSE_RANK trước phân trang theo điểm/wins/losses; tỷ lệ thắng một chữ số thập phân.

Campaign: người còn sống, không bỏ cuộc ở CAMPAIGN_CLEARED nhận WIN. Mỗi người phải thắng cửa trước cùng độ khó để vào cửa sau. Ranking theo số cửa thắng rồi tổng điểm tốt nhất mỗi cửa; cày lại cửa không cộng lặp các lần chơi. Arena và Campaign có query riêng, không trộn.

## 7. Bản lưu offline cá nhân

Backend chỉ nhận đồng bộ khi có mạng. Browser mất Internet dùng engine JS/IndexedDB/Service Worker ở UI hiện tại; API không thay thế lưu offline tại máy.

Ba slot/user. PUT expectedRevision=0 tạo mới; cập nhật gửi revision đã đọc. Khóa user bảo vệ insert đầu và UPDATE có điều kiện revision; conflict trả SAVE_002 để UI cho người dùng chọn bản giữ. Xóa hủy payload nhưng giữ metadata revision (V5); tạo lại slot vẫn nhận revision lớn hơn trước để thiết bị cũ không thể ghi đè. GET list chỉ metadata, GET slot mới đọc payload; DELETE cũng có revision. Không nhận userId.

Payload client không đáng tin, luôn tách offline_saves khỏi matches/match_players. Điểm tự khai không thể đi vào ranking online.

## 8. Bộ test và lệnh chạy

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean build
.\tools\test-postgres.ps1
```

| Nhóm                                            | Bảo vệ luồng                                                                                                              |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| AuthSystemTest                                  | JWT thật, CSRF, login/register, refresh đồng thời/replay có commit, revoke                                                |
| AuthApiErrorContractTest / AuthErrorHandlerTest | Lỗi custom vi/en, filter/OIDC handler, không lộ exception text                                                            |
| GameEngineTest                                  | Đạn/tường, cùng chết, item/buff, độ khó, wave, kill một lần, replay checkpoint, schema                                    |
| BusinessFlowTest                                | Room/API, tranh ghế/membership, kết toán đồng thời, history private, campaign unlock/rank, offline revision, profile/spec |
| BusinessFlowTest WebSocket                      | TCP thật, ticket một lần, Origin, input/sync, JSON lỗi, revoke đóng socket                                                |
| BusinessFlowTest recovery                       | Coordinator mới đọc snapshot, giữ buff/deadline, đủ người mới resume, tất cả disconnect thì pause                         |
| H2FilePersistenceTest                           | Đóng/mở pool cùng file, migration/bootstrap admin không nhân đôi, giữ tên/hash/session đã sửa                             |
| JUnit source contracts                                  | Không var, Instant, code/key/bundle, package/dependency direction, không hardcode prod secret                             |

H2 và PostgreSQL chạy cùng BusinessFlowTest; script PostgreSQL dùng 18.6 cục bộ, CI dùng service PostgreSQL 18. Test không coi là benchmark. Bằng chứng số lượng/kết quả cuối nằm trong STATUS. Chưa kiểm chứng kill process đúng thời điểm ghi, reverse proxy public, Google thật hay UI/offline browser.
