package vn.edu.utc.tankbattle.common.base.request;

/** Dùng chung quy tắc làm sạch chuỗi; không tự strip mật khẩu vì sẽ đổi bí mật người dùng. */
public abstract class BaseRequest {
    protected static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public void normalize() {}
}
