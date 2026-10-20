package vn.edu.utc.tankbattle.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;

/**
 * Adapter Bean Validation cho ValidationRules; dùng key của ValidateCode thay vì câu thông báo cố
 * định.
 */
public class TextConstraintValidator implements ConstraintValidator<ValidText, String> {
    private TextKind kind;

    /** Lưu TextKind từ annotation ValidText khi Bean Validation tạo validator cho trường. */
    @Override
    public void initialize(ValidText annotation) {
        kind = annotation.value();
    }

    /**
     * Ủy quyền kiểm tra sang ValidationRules và gắn template messageKey của enum khi sai. Cùng một
     * rule được dùng ở DTO/service; RestControllerAdvice tách template để trả đúng mã lỗi của
     * trường.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (ValidationRules.valid(kind, value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "{" + kind.getValidateCode().getMessageKey() + "}")
                .addConstraintViolation();
        return false;
    }
}
