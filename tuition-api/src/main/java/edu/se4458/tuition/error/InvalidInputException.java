package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 400: the input is malformed (format, range, CSV structure). */
public class InvalidInputException extends ApiException {

    public InvalidInputException(String detail) {
        this("invalid-input", detail);
    }

    public InvalidInputException(String problemType, String detail) {
        super(HttpStatus.BAD_REQUEST, problemType, "Invalid Input", detail);
    }
}
