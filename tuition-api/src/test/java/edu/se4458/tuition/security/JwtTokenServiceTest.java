package edu.se4458.tuition.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.se4458.tuition.config.JwtProperties;
import edu.se4458.tuition.config.SecurityConfig;
import edu.se4458.tuition.domain.Role;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Round trip: tokens issued by {@link JwtTokenService} are accepted by the decoder from {@link SecurityConfig}. */
class JwtTokenServiceTest {

    private static final JwtProperties PROPS =
            new JwtProperties("unit-test-only-secret-0123456789abcdef", "tuition-api", Duration.ofMinutes(60));

    private final SecurityConfig config = new SecurityConfig();
    private final JwtTokenService service = new JwtTokenService(config.jwtEncoder(PROPS), PROPS, Clock.systemUTC());
    private final JwtDecoder decoder = config.jwtDecoder(PROPS);

    @Test
    void studentToken_hasRoleStudentNoIssuerAndExpiry() {
        JwtTokenService.IssuedToken issued = service.issue("20230001", Role.STUDENT, "20230001");

        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("tuition-api");
        assertThat(jwt.getSubject()).isEqualTo("20230001");
        assertThat(jwt.getClaimAsString(TokenClaims.ROLE)).isEqualTo("STUDENT");
        assertThat(jwt.getClaimAsString(TokenClaims.STUDENT_NO)).isEqualTo("20230001");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void bankToken_hasNoStudentNo() {
        Jwt jwt = decoder.decode(service.issue("bank", Role.BANK, null).value());

        assertThat(jwt.getClaimAsString(TokenClaims.ROLE)).isEqualTo("BANK");
        assertThat(jwt.hasClaim(TokenClaims.STUDENT_NO)).isFalse();
    }

    @Test
    void properties_rejectShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "tuition-api", Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void properties_rejectMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties("", "tuition-api", Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
