package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 401: the caller could not be authenticated (e.g. wrong username or password at login). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String problemType, String detail) {
        super(HttpStatus.UNAUTHORIZED, problemType, "Unauthorized", detail);
    }
}
