package vn.edu.utc.tankbattle.auth.exception;

import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/** Lỗi xác thực dự kiến. Khi phát hiện replay, transaction thu hồi vẫn phải được commit. */
public class AuthFailure extends BusinessException {
    public AuthFailure(ValidateCode validateCode, Object... arguments) {
        super(validateCode, arguments);
    }
}
