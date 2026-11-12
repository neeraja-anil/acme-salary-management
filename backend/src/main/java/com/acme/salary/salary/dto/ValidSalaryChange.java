package com.acme.salary.salary.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Cross-field rules for a salary change request: the reason must be one a user may choose
 * (not NEW_HIRE), and a note is required when the reason is OTHER.
 */
@Documented
@Constraint(validatedBy = SalaryChangeRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSalaryChange {

    String message() default "invalid salary change";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
