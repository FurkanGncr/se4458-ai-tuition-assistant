package edu.se4458.tuition.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.se4458.tuition.error.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentAccessPolicyTest {

    private final StudentAccessPolicy policy = new StudentAccessPolicy();

    @Test
    void student_canAccessOwnNumber() {
        assertThatCode(() -> policy.checkCanAccess(token("STUDENT", "20230001"), "20230001"))
                .doesNotThrowAnyException();
    }

    @Test
    void student_cannotAccessAnotherStudent() {
        assertThatThrownBy(() -> policy.checkCanAccess(token("STUDENT", "20230001"), "20230003"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentTokenWithoutStudentNo_isDenied() {
        assertThatThrownBy(() -> policy.checkCanAccess(token("STUDENT", null), "20230001"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void bank_canAccessAnyStudent() {
        assertThatCode(() -> policy.checkCanAccess(token("BANK", null), "20230003"))
                .doesNotThrowAnyException();
    }

    @Test
    void admin_isDenied() {
        assertThatThrownBy(() -> policy.checkCanAccess(token("ADMIN", null), "20230001"))
                .isInstanceOf(ForbiddenException.class);
    }

    private static Jwt token(String role, String studentNo) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("user").claim(TokenClaims.ROLE, role);
        if (studentNo != null) {
            b.claim(TokenClaims.STUDENT_NO, studentNo);
        }
        return b.build();
    }
}
