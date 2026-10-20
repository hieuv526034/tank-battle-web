package vn.edu.utc.tankbattle.common.util;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa định danh/tên; không dùng bất cứ hàm này để sửa mật khẩu. */
public final class TextNormalizer {
    /** Ẩn constructor của các hàm chuẩn hóa chuỗi dùng chung. */
    private TextNormalizer() {}

    /**
     * Cắt khoảng trắng hai đầu và chuyển username về chữ thường bằng Locale.ROOT. Null thành chuỗi
     * rỗng để validator trả mã lỗi thống nhất; Locale.ROOT tránh kết quả thay đổi theo ngôn ngữ máy
     * chủ.
     */
    public static String username(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Cắt khoảng trắng hai đầu và chuẩn hóa Unicode NFC cho tên hiển thị/phòng. Giữ nguyên chữ hoa
     * và dấu tiếng Việt; chuẩn hóa xong mới kiểm tra ký tự/độ dài.
     */
    public static String name(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }

    /** Cắt khoảng trắng và chuyển mã phòng về chữ hoa bằng Locale.ROOT trước validate/query. */
    public static String roomCode(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
