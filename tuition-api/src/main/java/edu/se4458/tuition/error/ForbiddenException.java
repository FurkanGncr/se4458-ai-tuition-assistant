package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/** 403: the caller is authenticated but may not access this resource (e.g. another student's tuition). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String problemType, String detail) {
        super(HttpStatus.FORBIDDEN, problemType, "Forbidden", detail);
    }
}
