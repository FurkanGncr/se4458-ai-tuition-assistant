package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 422: the request is well-formed but breaks a business rule (e.g. payment exceeds the balance). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String problemType, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problemType, "Business Rule Violation", detail);
    }
}
