package vn.edu.utc.tankbattle.common.exception;

import java.util.Objects;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** Lỗi chỉ mang mã/arguments; việc chọn ngôn ngữ thuộc tầng trả response. */
public class BusinessException extends RuntimeException {
    private final ValidateCode validateCode;
    private final Object[] arguments;

    public BusinessException(ValidateCode validateCode, Object... arguments) {
        super(Objects.requireNonNull(validateCode).getMessageKey());
        this.validateCode = validateCode;
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    public ValidateCode getValidateCode() {
        return validateCode;
    }

    public Object[] getArguments() {
        return arguments.clone();
    }

    public static BusinessException of(ValidateCode validateCode, Object... arguments) {
        return new BusinessException(validateCode, arguments);
    }
}
