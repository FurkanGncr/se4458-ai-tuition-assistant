package edu.se4458.tuition.error;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to RFC 7807 Problem Details ({@code application/problem+json}).
 * Framework errors (malformed JSON, missing parameter, wrong type, 404/405) are handled by the base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String TYPE_PREFIX = "urn:tuition:problem:";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return problem(ex.getStatus(), ex.getProblemType(), ex.getTitle(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database constraint violated: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "data-conflict", "Conflict",
                "The request conflicts with existing data. Retry or check the input.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred.");
    }

    /** Invalid @RequestBody fields. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldError(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(e -> e.get("field")))
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    /** Invalid @PathVariable / @RequestParam values. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(err -> fieldError(result.getMethodParameter().getParameterName(),
                                err.getDefaultMessage())))
                .sorted(Comparator.comparing(e -> e.get("field")))
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    private static ProblemDetail validationProblem(HttpStatusCode status, List<Map<String, String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, "One or more fields are invalid.");
        pd.setType(URI.create(TYPE_PREFIX + "validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", Objects.requireNonNullElse(field, "unknown"),
                "message", Objects.requireNonNullElse(message, "invalid value"));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_PREFIX + type));
        pd.setTitle(title);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
