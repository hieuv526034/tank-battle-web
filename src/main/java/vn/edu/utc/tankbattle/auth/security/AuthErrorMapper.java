package vn.edu.utc.tankbattle.auth.security;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/**
 * Chuyển loại exception sang catalog, không phân tích câu chữ của exception. DB/dịch vụ auth hỏng
 * cần trả 503 để client không hiểu nhầm là sai mật khẩu.
 */
public final class AuthErrorMapper {
    /** Ẩn constructor của bộ ánh xạ exception thuần dùng cho các handler auth. */
    private AuthErrorMapper() {}

    /**
     * Chọn 503 cho lỗi hạ tầng; các lỗi đăng nhập còn lại về AUTH_INVALID_CREDENTIALS. Phân loại
     * theo kiểu exception, không phụ thuộc câu chữ của thư viện.
     */
    public static ValidateCode loginFailure(Throwable failure) {
        return infrastructureFailure(failure)
                ? ValidateCode.SYS_SERVICE_UNAVAILABLE
                : ValidateCode.AUTH_INVALID_CREDENTIALS;
    }

    /**
     * Chọn 503 khi kiểm chứng token gặp lỗi dịch vụ/DB; token không hợp lệ trả AUTH_INVALID_TOKEN.
     */
    public static ValidateCode tokenFailure(Throwable failure) {
        return infrastructureFailure(failure)
                ? ValidateCode.SYS_SERVICE_UNAVAILABLE
                : ValidateCode.AUTH_INVALID_TOKEN;
    }

    /** Chọn 503 cho hạ tầng auth hỏng; lỗi luồng Google còn lại trả AUTH_OAUTH_FAILED. */
    public static ValidateCode googleFailure(Throwable failure) {
        return infrastructureFailure(failure)
                ? ValidateCode.SYS_SERVICE_UNAVAILABLE
                : ValidateCode.AUTH_OAUTH_FAILED;
    }

    /**
     * Duyệt cause tối đa 10 mức để nhận diện DataAccessException hoặc
     * AuthenticationServiceException. Giới hạn độ sâu tránh chuỗi cause bất thường làm xử lý lỗi
     * chạy mãi.
     */
    private static boolean infrastructureFailure(Throwable failure) {
        // Giới hạn độ sâu để tránh vòng cause bất thường từ thư viện bên ngoài.
        for (int depth = 0; failure != null && depth < 10; depth++, failure = failure.getCause()) {
            if (failure instanceof DataAccessException
                    || failure instanceof AuthenticationServiceException) {
                return true;
            }
        }
        return false;
    }
}
