package com.acme.salary.salary.dto;

import com.acme.salary.salary.SalaryChangeReason;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Reports each violation on the field it concerns, so clients can show it next to that input. */
public class SalaryChangeRequestValidator implements ConstraintValidator<ValidSalaryChange, SalaryChangeRequest> {

    @Override
    public boolean isValid(SalaryChangeRequest request, ConstraintValidatorContext context) {
        if (request == null || request.reason() == null) {
            return true; // @NotNull on the field reports a missing reason
        }
        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (!request.reason().isUserSelectable()) {
            context.buildConstraintViolationWithTemplate("is set by the system and cannot be chosen")
                    .addPropertyNode("reason").addConstraintViolation();
            valid = false;
        }
        if (request.reason() == SalaryChangeReason.OTHER && (request.note() == null || request.note().isBlank())) {
            context.buildConstraintViolationWithTemplate("is required when the reason is OTHER")
                    .addPropertyNode("note").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
