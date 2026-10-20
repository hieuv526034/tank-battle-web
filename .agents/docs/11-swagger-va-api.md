# API, DTO và Swagger

Swagger local /swagger-ui.html; spec /v3/api-docs. Production tắt mặc định. Controller có Operation tiếng Việt, DTO Schema và StandardApiErrors. Giao thức socket, transaction và kiểm thử đọc [tài liệu 20](20-luong-backend-va-test.md).

## Envelope và bảo mật

ApiResponse gồm code/messageKey/message/locale/data/errors; SYS_000 khi thành công. Lỗi trường là errors[field] chứa code/key/message, không có rejectedValue. Accept-Language vi/en. Không trả SQL/exception text/password.

API nghiệp vụ dùng Bearer. POST `/api/auth/**` có CSRF vì có refresh cookie; logout/logout-all/socket-ticket yêu cầu Bearer **AND** CSRF trong OpenAPI. Session web không thay JWT ở API.

Mọi REST operation `/api/**` và `/auth/token` có tham số header **Accept-Language** trong Swagger, tham chiếu component dùng chung: chọn `vi` hoặc `en`, mặc định `vi`. Không phải chép lại parameter ở từng controller. Header chọn ngôn ngữ cả response thành công, lỗi nghiệp vụ, validation và security; code/messageKey không thay đổi. `en-US` dùng tiếng Anh; ngôn ngữ không hỗ trợ quay về tiếng Việt, hoặc chọn ngôn ngữ hỗ trợ tiếp theo trong danh sách q-value.

## Endpoint đang có

`GET /api/admin/me` trả AdminProfileResponse (id/username/displayName/role), bắt buộc Bearer ADMIN. USER nhận 403/AUTH_003, khách nhận 401/AUTH_001; GET không cần CSRF. Toàn bộ `/api/admin/**` dùng matcher ROLE_ADMIN. Luồng tài khoản quản trị lần đầu xem [bootstrap admin](24-bootstrap-admin-va-phan-quyen.md).

| Method/path                                 | Request → response                       | Điều kiện                                                 |
| ------------------------------------------- | ---------------------------------------- | --------------------------------------------------------- |
| GET /api/auth/csrf                          | CsrfResponse                             | Lấy cookie XSRF-TOKEN và data.token                       |
| POST /api/auth/register                     | RegisterRequest → UserResponse           | CSRF, chưa tự login                                       |
| POST /api/auth/login                        | LoginRequest → AuthTokenResponse         | CSRF, refresh cookie HttpOnly                             |
| POST /api/auth/refresh                      | Cookie → AuthTokenResponse               | CSRF, không gửi Bearer cũ                                 |
| GET /api/auth/me                            | UserResponse                             | Bearer                                                    |
| POST /api/auth/logout                       | Void                                     | Bearer+CSRF, revoke sid                                   |
| POST /api/auth/logout-all                   | Void                                     | Bearer+CSRF, revoke mọi sid                               |
| POST /api/auth/socket-ticket                | SocketTicketResponse                     | Bearer+CSRF, dùng một lần/30s                             |
| POST /auth/token                            | AuthTokenResponse                        | Session web+CSRF của session                              |
| PUT /api/profile                            | UpdateProfileRequest → UserResponse      | Đổi tên của chính mình                                    |
| GET /api/rooms                              | List RoomResponse                        | Tối đa 40 phòng, member theo lô                           |
| GET /api/rooms/current                      | RoomResponse hoặc null                   | Phòng của JWT hiện tại                                    |
| POST /api/rooms                             | CreateRoomRequest → RoomResponse         | Tạo phòng và membership cùng transaction                  |
| POST /api/rooms/{code}/join                 | RoomResponse                             | WAITING/còn chỗ, join cùng phòng idempotent               |
| GET /api/rooms/{code}                       | RoomRuntimeResponse                      | Thành viên mới đọc                                        |
| PUT /api/rooms/{code}/ready                 | ReadyRequest                             | WAITING, chỉ chính mình                                   |
| POST /api/rooms/{code}/start                | StartMatchRequest → StartMatchResponse   | Chủ phòng, all ready/socket, đủ unlock                    |
| DELETE /api/rooms/{code}/members/me         | Void                                     | Bỏ cuộc bền vững nếu đang chơi                            |
| GET /api/matches                            | List MatchSummaryResponse                | Lịch sử của tôi, không tải payload                        |
| GET /api/matches/{id}                       | MatchSummaryResponse                     | Người tham gia                                            |
| GET /api/matches/{id}/results               | List ResultResponse                      | HP/outcome null khi chưa kết toán, kills/score mặc định 0 |
| GET /api/matches/{id}/state                 | GameFrameResponse                        | Live RAM hoặc snapshot terminal                           |
| POST /api/matches/{id}/resume-ready         | Void                                     | Còn sống/member/socket, trong hạn                         |
| POST /api/matches/{id}/cancel-recovery      | Void                                     | RECOVERING; ABORTED không cộng ranking                    |
| GET /api/rankings                           | List RankResponse                        | Arena FINISHED, phân trang                                |
| GET /api/rankings/me                        | RankResponse hoặc null                   | Hạng Arena của tôi                                        |
| GET /api/rankings/campaign                  | List CampaignRankResponse                | Theo difficulty                                           |
| GET /api/rankings/campaign/me               | CampaignRankResponse hoặc null           | Hạng Campaign toàn bảng của tôi                           |
| GET /api/rankings/campaign/me/progress      | List số cửa đã WIN                       | Theo difficulty                                           |
| GET /api/game/catalog                       | GameCatalogResponse                      | Rules, catalog, cửa/wave                                  |
| GET /api/offline-saves                      | List metadata OfflineSaveResponse        | Ba slot của tôi, state=null                               |
| GET /api/offline-saves/{slot}               | OfflineSaveResponse                      | Slot 1–3 của tôi                                          |
| PUT /api/offline-saves/{slot}               | OfflineSaveRequest → OfflineSaveResponse | Revision khớp; không cộng ranking                         |
| DELETE /api/offline-saves/{slot}?revision=2 | Void                                     | Revision khớp                                             |
| GET /actuator/health/readiness              | Health                                   | Có kiểm tra DB                                            |

Query page từ 0, tối đa 10000; size 1–50, mặc định 20. Query difficulty mặc định NORMAL. Không nhận userId từ request. Enum/query parameter sai hoặc thiếu tham số SYS_002; JSON hỏng SYS_003; DB ngoài dự kiến SYS_006.

## Start và WebSocket

```json
{ "mode": "CAMPAIGN", "difficulty": "NORMAL", "stage": 1 }
```

Không body thì ARENA/NORMAL/1. Arena 2–4, Campaign 1–4; mọi người ready/socket. Cửa 2–3 yêu cầu mọi thành viên đã WIN cửa trước cùng độ khó.

Lấy vé sau khi tham gia phòng, mở /ws/game?ticket=OPAQUE_TICKET. Không dùng JWT trong URL. Message ví dụ:

```json
{ "type": "input", "matchId": "UUID_FROM_START", "sequence": 42, "direction": "LEFT", "fire": true }
```

type subscribe phát lobby hiện tại; type sync cần matchId; input chỉ nhận ý định. Event LOBBY/GAME/FINISHED và ERROR; ERROR chứa cùng envelope custom. Xem giới hạn/refresh/reconnect trong tài liệu 20.

## Thử theo luồng

CSRF → register/login → Authorize Bearer → create/join → ticket/socket → ready/start → input/sync → results/history/ranking. Recovery: mở lại socket, GET state/lobby, mọi người sống POST resume-ready. Offline sync: PUT revision 0, lần sau dùng revision trả về; conflict cần tải lại và lựa chọn ở UI.

BusinessFlowTest kiểm tra header trên toàn bộ REST operation trong spec, security AND cho vé, sáu trường hợp locale và lỗi WebSocket tiếng Việt/Anh qua kết nối thật. AuthSystemTest kiểm tra spec auth. Chưa thao tác Swagger UI bằng browser; Google thật cần OAuth Client riêng.
