package edu.se4458.tuition.security;

import edu.se4458.tuition.domain.Role;
import edu.se4458.tuition.error.ForbiddenException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Ownership rule for account-specific operations. A STUDENT token may only access its own student number;
 * a BANK token may access any student. Every other role is denied.
 *
 * <p>The student number always comes from the verified token, never from the request, so supplying another
 * student's number (for example in an AI agent prompt) is not enough to read or pay their tuition.
 */
public class StudentAccessPolicy {

    public void checkCanAccess(Jwt token, String studentNo) {
        String role = token.getClaimAsString(TokenClaims.ROLE);
        if (Role.BANK.name().equals(role)) {
            return;
        }
        if (Role.STUDENT.name().equals(role) && studentNo != null
                && studentNo.equals(token.getClaimAsString(TokenClaims.STUDENT_NO))) {
            return;
        }
        throw new ForbiddenException("forbidden", "You are not allowed to access the tuition of another student");
    }
}
