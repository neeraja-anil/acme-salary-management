package com.acme.salary.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 422: the request is well-formed but violates a business rule.
 * {@code rule} names the rule so clients can react to it specifically.
 */
public class BusinessRuleException extends ApiException {

    private final String rule;

    public BusinessRuleException(String rule, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", "Business rule violated", detail);
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("rule", rule);
    }
}
