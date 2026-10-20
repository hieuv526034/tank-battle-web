package vn.edu.utc.tankbattle.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;

/** Constraint chọn rule theo domain; validator tự gắn message key từ ValidateCode. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextConstraintValidator.class)
public @interface ValidText {
    TextKind value();

    String message() default "{system.invalid_request}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
