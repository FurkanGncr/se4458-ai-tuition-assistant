package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 409: the request conflicts with stored data (duplicate transaction ID, amount below paid, currency change). */
public class ConflictException extends ApiException {

    public ConflictException(String problemType, String detail) {
        super(HttpStatus.CONFLICT, problemType, "Conflict", detail);
    }
}
