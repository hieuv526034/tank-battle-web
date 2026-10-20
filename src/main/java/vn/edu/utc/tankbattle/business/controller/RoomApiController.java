package vn.edu.utc.tankbattle.business.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.utc.tankbattle.business.dto.request.CreateRoomRequest;
import vn.edu.utc.tankbattle.business.dto.request.ReadyRequest;
import vn.edu.utc.tankbattle.business.dto.request.StartMatchRequest;
import vn.edu.utc.tankbattle.business.dto.response.RoomResponse;
import vn.edu.utc.tankbattle.business.dto.response.RoomRuntimeResponse;
import vn.edu.utc.tankbattle.business.dto.response.StartMatchResponse;
import vn.edu.utc.tankbattle.business.runtime.GameCoordinator;
import vn.edu.utc.tankbattle.business.service.RoomService;
import vn.edu.utc.tankbattle.common.documentation.StandardApiErrors;
import vn.edu.utc.tankbattle.common.dto.ApiResponse;
import vn.edu.utc.tankbattle.common.i18n.ApiResponseFactory;

/** UserId luôn lấy từ JWT; phát lobby sau khi service transaction đã commit. */
@RestController
@RequestMapping("/api/rooms")
@Tag(name = "03. Phòng chơi")
@SecurityRequirement(name = "bearerAuth")
@StandardApiErrors
public class RoomApiController {
    private final RoomService rooms;
    private final GameCoordinator games;
    private final ApiResponseFactory responses;

    public RoomApiController(
            RoomService rooms, GameCoordinator games, ApiResponseFactory responses) {
        this.rooms = rooms;
        this.games = games;
        this.responses = responses;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách phòng chưa đóng",
            description = "Tối đa 40 phòng; lấy member theo lô, không query từng phòng.")
    public ApiResponse<List<RoomResponse>> list() {
        return responses.success(rooms.list());
    }

    @GetMapping("/current")
    @Operation(
            summary = "Phòng hiện tại của tôi",
            description = "data=null khi chưa tham gia; không nhận userId từ client.")
    public ApiResponse<RoomResponse> current(@AuthenticationPrincipal Jwt principal) {
        return responses.success(
                rooms.current(Long.parseLong(principal.getSubject())).orElse(null));
    }

    @PostMapping
    @Operation(
            summary = "Tạo phòng",
            description =
                    "Tạo ghế chủ phòng cùng transaction. ROOM_007 nếu đã ở phòng; ROOM_011 khi đủ maxRooms.")
    public ApiResponse<RoomResponse> create(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody CreateRoomRequest request) {
        return responses.success(
                rooms.create(
                        Long.parseLong(principal.getSubject()),
                        request.getName(),
                        request.getCapacity()));
    }

    @PostMapping("/{code}/join")
    @Operation(
            summary = "Tham gia theo mã phòng",
            description =
                    "ROOM_004 phòng đầy, ROOM_005 không ở WAITING, ROOM_007 đang ở phòng khác.")
    public ApiResponse<RoomResponse> join(
            @AuthenticationPrincipal Jwt principal, @PathVariable String code) {
        RoomResponse room = rooms.join(Long.parseLong(principal.getSubject()), code);
        games.publishRoom(room.code());
        return responses.success(room);
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Lobby và trạng thái runtime",
            description = "Chỉ thành viên phòng. ROOM_006 nếu chưa tham gia.")
    public ApiResponse<RoomRuntimeResponse> detail(
            @AuthenticationPrincipal Jwt principal, @PathVariable String code) {
        rooms.requireMember(rooms.byCode(code), Long.parseLong(principal.getSubject()));
        return responses.success(games.roomState(code));
    }

    @PutMapping("/{code}/ready")
    @Operation(
            summary = "Bật/tắt sẵn sàng",
            description = "Chỉ đổi ready của chính mình khi phòng WAITING.")
    public ApiResponse<Void> ready(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String code,
            @Valid @RequestBody ReadyRequest request) {
        rooms.ready(Long.parseLong(principal.getSubject()), code, request.isReady());
        games.publishRoom(code);
        return responses.success(null);
    }

    @PostMapping("/{code}/start")
    @Operation(
            summary = "Chủ phòng bắt đầu Arena hoặc Campaign",
            description =
                    "Arena 2–4 người, Campaign 1–4 người. Tất cả ready/kết nối socket; cửa kế cần WIN cửa trước cùng độ khó.")
    public ApiResponse<StartMatchResponse> start(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String code,
            @Valid @RequestBody(required = false) StartMatchRequest request) {
        return responses.success(
                new StartMatchResponse(
                        games.start(
                                Long.parseLong(principal.getSubject()),
                                code,
                                request == null ? new StartMatchRequest() : request)));
    }

    @DeleteMapping("/{code}/members/me")
    @Operation(
            summary = "Rời phòng/bỏ cuộc",
            description =
                    "Ghi mốc bỏ cuộc bền vững. Chủ phòng rời thì chuyển quyền; phòng rỗng chuyển CLOSED.")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal Jwt principal, @PathVariable String code) {
        games.leave(Long.parseLong(principal.getSubject()), code);
        return responses.success(null);
    }
}
