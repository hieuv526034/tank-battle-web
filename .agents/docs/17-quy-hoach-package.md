# Cấu trúc đã áp dụng: auth, common, business

Đã chuyển source thực tế theo yêu cầu, không còn là bản đề xuất. Đã xóa các thư mục source cũ rỗng và kiểm tra package khớp đường dẫn.

```text
vn.edu.utc.tankbattle
  TankBattleApplication
  auth/
    config/ security/ filter/ controller/ service/ repository/
    entity/ dto/request/ dto/response/ mapper/ model/ exception/
  common/
    base/entity/ base/request/ base/response/ base/repository/
    constant/ config/ dto/request/ exception/ validation/
    i18n/ util/ event/ model/ documentation/
  business/
    controller/       Room, Match, Ranking, GameCatalog, OfflineSave
    service/          RoomService, MatchService, RankingService, OfflineSaveService
    repository/       SQL, projection, FOR UPDATE và batch
    mapper/           Chuyển row sang DTO
    entity/           Room, membership, match, kết quả, snapshot, offline save
    dto/request/      CreateRoom, Ready, StartMatch, Socket, OfflineSave
    dto/response/     Response có kiểu, schema Swagger
    model/            State game, địch/item/buff, projection nội bộ
    config/           Nạp catalog/campaign và đăng ký socket
    engine/           GameEngine, CampaignEngine, CombatGeometry, StateCodec
    runtime/          GameCoordinator và GameRuntime
    websocket/        Handler, SocketHub, listener thu hồi phiên
```

Business chia theo tầng chung; không tạo business/room/entity, business/match/dto... Không khai báo model/DTO làm class lồng trong service. auth và business không import lẫn nhau: danh tính dùng common.model.SocketIdentity, thu hồi phiên qua event chung. WebSocketConfig nhận hợp đồng HandshakeInterceptor của Spring, implementation xác thực nằm ở auth.

Base nằm đúng common/base. Entity kế thừa audit; request kế thừa normalize; response triển khai BaseResponse; UserRepository dùng BaseRepository. Các lớp JDBC không bị ép thành JPA repository chỉ để có kế thừa hình thức.

Theo dõi luồng cụ thể trong [tài liệu 20](20-luong-backend-va-test.md); test kiến trúc ở src/test/java/vn/edu/utc/tankbattle/architecture/SourceContractTest.java ngăn tạo lại package tính năng ở gốc.
