package edu.se4458.tuition.security;

import edu.se4458.tuition.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses from the security filter chain as RFC 7807 Problem Details, in the same format
 * as {@link GlobalExceptionHandler}, with an RFC 6750 {@code WWW-Authenticate: Bearer ...} header. Token decoding
 * details are not echoed back to the client.
 */
public class ProblemDetailsAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public ProblemDetailsAuthHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 401: no token, or the token is invalid or expired. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean badToken = ex instanceof OAuth2AuthenticationException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        String detail = badToken
                ? "The access token is invalid or expired."
                : "Authentication is required. Send 'Authorization: Bearer <token>' from POST /api/v1/auth/login.";
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", detail);
    }

    /** 403: the token is valid but its role may not use this route. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "Your role is not allowed to use this operation.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String type,
            String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", GlobalExceptionHandler.TYPE_PREFIX + type);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
