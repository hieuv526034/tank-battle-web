package vn.edu.utc.tankbattle.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;
import org.springframework.beans.BeanWrapperImpl;

/** So sánh hai trường theo annotation, gắn lỗi vào trường xác nhận để UI hiển thị đúng vị trí. */
public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {
    private FieldsMatch rule;

    /**
     * Lưu tên trường gốc, trường xác nhận và ValidateCode cấu hình trong annotation FieldsMatch.
     */
    @Override
    public void initialize(FieldsMatch rule) {
        this.rule = rule;
    }

    /**
     * So sánh hai thuộc tính của DTO, thường dùng password và confirmPassword. Nếu khác nhau, gắn
     * violation vào trường xác nhận với messageKey của enum để UI đặt lỗi đúng ô nhập; bean null để
     * constraint bắt buộc khác xử lý.
     */
    @Override
    public boolean isValid(Object bean, ConstraintValidatorContext context) {
        if (bean == null) {
            return true;
        }
        BeanWrapperImpl properties = new BeanWrapperImpl(bean);
        if (Objects.equals(
                properties.getPropertyValue(rule.field()),
                properties.getPropertyValue(rule.confirm()))) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{" + rule.code().getMessageKey() + "}")
                .addPropertyNode(rule.confirm())
                .addConstraintViolation();
        return false;
    }
}
