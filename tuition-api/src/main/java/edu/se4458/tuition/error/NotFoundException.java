package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 404: the student or the tuition record does not exist. */
public class NotFoundException extends ApiException {

    public NotFoundException(String problemType, String detail) {
        super(HttpStatus.NOT_FOUND, problemType, "Not Found", detail);
    }
}
