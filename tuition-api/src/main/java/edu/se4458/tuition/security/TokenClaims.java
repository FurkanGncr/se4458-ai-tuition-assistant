package edu.se4458.tuition.security;

/** Custom claim names in the access token (besides the standard iss, sub, iat, exp). */
public final class TokenClaims {

    /** The user's {@link edu.se4458.tuition.domain.Role}, e.g. {@code STUDENT}. */
    public static final String ROLE = "role";

    /** The linked student number; present only for STUDENT tokens. */
    public static final String STUDENT_NO = "studentNo";

    private TokenClaims() {
    }
}
