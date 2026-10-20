package vn.edu.utc.tankbattle.common.documentation;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Envelope lỗi chung; mã cụ thể theo route nằm trong tài liệu API và catalog ValidateCode. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
    @ApiResponse(
            responseCode = "400",
            description =
                    "SYS_002: validation, có lỗi từng trường; SYS_003: JSON sai kiểu/định dạng.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class),
                            examples =
                                    @ExampleObject(
                                            name = "Validation",
                                            value =
                                                    """
                        {"code":"SYS_002","messageKey":"system.invalid_request",
                         "message":"Kiểm tra lại dữ liệu nhập.","locale":"vi","data":null,
                         "errors":{"username":{"code":"USER_002","messageKey":"user.username.invalid",
                         "message":"Username cần 4–24 chữ, số hoặc dấu gạch dưới."}}}
                        """))),
    @ApiResponse(
            responseCode = "401",
            description =
                    "AUTH_001 chưa login; AUTH_002 sai thông tin; AUTH_005 JWT; AUTH_006 refresh; AUTH_007 replay; AUTH_008 Google.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class),
                            examples =
                                    @ExampleObject(
                                            name = "JWT không hợp lệ",
                                            value =
                                                    """
                        {"code":"AUTH_005","messageKey":"auth.invalid_token",
                         "message":"Access token không hợp lệ, hết hạn hoặc đã bị thu hồi.",
                         "locale":"vi","data":null,"errors":{}}
                        """))),
    @ApiResponse(
            responseCode = "403",
            description = "AUTH_003 thiếu quyền hoặc AUTH_004 thiếu/sai CSRF.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class))),
    @ApiResponse(
            responseCode = "409",
            description = "Xung đột dữ liệu; đăng ký trùng username trả USER_006.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class))),
    @ApiResponse(
            responseCode = "429",
            description = "SYS_004: quá số lần gọi; chờ số giây trong Retry-After.",
            headers =
                    @Header(
                            name = "Retry-After",
                            schema = @Schema(type = "integer", example = "60")),
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class))),
    @ApiResponse(
            responseCode = "500",
            description = "SYS_001: lỗi ngoài dự kiến; không trả stack trace/thông tin nội bộ.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class))),
    @ApiResponse(
            responseCode = "503",
            description = "SYS_006: DB hoặc dịch vụ xác thực tạm thời không khả dụng.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.edu.utc.tankbattle.common.dto.ApiResponse
                                                            .class)))
})
public @interface StandardApiErrors {}
