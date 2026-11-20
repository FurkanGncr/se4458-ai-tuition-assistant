package edu.se4458.tuition.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for business errors. {@link GlobalExceptionHandler} turns these into RFC 7807 problem details
 * with type {@code urn:tuition:problem:<problemType>}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String problemType;
    private final String title;

    protected ApiException(HttpStatus status, String problemType, String title, String detail) {
        super(detail);
        this.status = status;
        this.problemType = problemType;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getProblemType() {
        return problemType;
    }

    public String getTitle() {
        return title;
    }
}
