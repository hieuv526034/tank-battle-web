package vn.edu.utc.tankbattle.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** Kiểm tra hai trường bằng nhau, lỗi gắn vào trường confirm để UI hiển thị đúng vị trí. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsMatchValidator.class)
public @interface FieldsMatch {
    String field();

    String confirm();

    ValidateCode code();

    String message() default "{system.invalid_request}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
