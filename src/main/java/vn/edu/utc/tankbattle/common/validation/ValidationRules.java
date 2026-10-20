package vn.edu.utc.tankbattle.common.validation;

import java.util.regex.Pattern;
import vn.edu.utc.tankbattle.common.constant.AppConstants;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/**
 * Hàm thuần, không query DB. Tên tiếng Việt dùng Unicode letters/marks/numbers; dấu chấm, cách,
 * gạch dưới/ngang được phép, control/markup bị từ chối.
 */
public final class ValidationRules {
    private static final Pattern USERNAME = Pattern.compile(AppConstants.USERNAME_PATTERN);
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}\\p{N} ._-]+");

    /** Ẩn constructor của bộ rule thuần dùng chung cho DTO và service. */
    private ValidationRules() {}

    /**
     * Chọn quy tắc theo TextKind và trả boolean, không ném lỗi hoặc đọc DB. Username theo pattern,
     * tên hỗ trợ Unicode, password theo độ dài/NUL và mã phòng theo alphabet. Caller chuẩn hóa các
     * trường định danh/tên trước khi gọi.
     */
    public static boolean valid(TextKind kind, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (kind) {
            case USERNAME -> USERNAME.matcher(value).matches();
            case DISPLAY_NAME ->
                    validName(
                            value,
                            AppConstants.DISPLAY_NAME_MIN_LENGTH,
                            AppConstants.DISPLAY_NAME_MAX_LENGTH);
            case ROOM_NAME ->
                    validName(
                            value,
                            AppConstants.ROOM_NAME_MIN_LENGTH,
                            AppConstants.ROOM_NAME_MAX_LENGTH);
            case PASSWORD ->
                    between(
                                    value,
                                    AppConstants.PASSWORD_MIN_LENGTH,
                                    AppConstants.PASSWORD_MAX_LENGTH)
                            && value.indexOf('\0') < 0;
            case ROOM_CODE ->
                    value.length() == AppConstants.ROOM_CODE_LENGTH
                            && value.chars()
                                    .allMatch(
                                            character ->
                                                    AppConstants.ROOM_CODE_ALPHABET.indexOf(
                                                                    character)
                                                            >= 0);
        };
    }

    /**
     * Áp cùng rule trong service để caller nội bộ cũng giữ invariant như API @Valid. Khi sai, ném
     * BusinessException bằng ValidateCode gắn với TextKind.
     */
    public static void require(TextKind kind, String value) {
        if (!valid(kind, value)) {
            throw BusinessException.of(kind.getValidateCode());
        }
    }

    /**
     * Kiểm tra tên đã trim, độ dài theo code point và tập chữ/số/dấu/ký tự được phép. Dùng chung
     * cho tên hiển thị và tên phòng với cặp min/max riêng.
     */
    private static boolean validName(String value, int min, int max) {
        return value.equals(value.strip())
                && between(value, min, max)
                && NAME.matcher(value).matches();
    }

    /**
     * Đếm Unicode code point thay vì số UTF-16 char để kiểm tra độ dài tên/mật khẩu. Chặn nhanh
     * chuỗi quá lớn trước khi đếm rồi so khoảng min/max.
     */
    private static boolean between(String value, int min, int max) {
        if (value.length() > max * 2) {
            return false;
        }
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
