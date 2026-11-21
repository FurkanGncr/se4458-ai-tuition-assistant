package edu.se4458.tuition.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.se4458.tuition.config.JwtProperties;
import edu.se4458.tuition.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds real signed tokens for controller tests, using the production {@link JwtTokenService}, so the tests go
 * through the real decoder, issuer check and role mapping. Register with {@code @Import(TestTokens.class)}.
 * Every method returns a full {@code Authorization} header value ("Bearer ...").
 */
public class TestTokens {

    private final JwtTokenService valid;
    private final JwtTokenService expired;
    private final JwtTokenService otherKey;
    private final JwtTokenService otherIssuer;

    public TestTokens(JwtEncoder encoder, JwtProperties properties) {
        this.valid = new JwtTokenService(encoder, properties, Clock.systemUTC());
        // Issued 2 hours ago with a 60 min lifetime: expired well beyond the 60 s clock skew.
        this.expired = new JwtTokenService(encoder, properties,
                Clock.offset(Clock.systemUTC(), properties.ttl().plus(Duration.ofHours(1)).negated()));
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "a-different-secret-the-api-does-not-know".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        this.otherKey = new JwtTokenService(foreignEncoder, properties, Clock.systemUTC());
        this.otherIssuer = new JwtTokenService(encoder,
                new JwtProperties(properties.secret(), "someone-else", properties.ttl()), Clock.systemUTC());
    }

    public String student(String studentNo) {
        return bearer(valid, studentNo, Role.STUDENT, studentNo);
    }

    public String bank() {
        return bearer(valid, "bank", Role.BANK, null);
    }

    public String admin() {
        return bearer(valid, "admin", Role.ADMIN, null);
    }

    public String expiredStudent(String studentNo) {
        return bearer(expired, studentNo, Role.STUDENT, studentNo);
    }

    public String studentSignedWithOtherKey(String studentNo) {
        return bearer(otherKey, studentNo, Role.STUDENT, studentNo);
    }

    public String studentWithOtherIssuer(String studentNo) {
        return bearer(otherIssuer, studentNo, Role.STUDENT, studentNo);
    }

    private static String bearer(JwtTokenService service, String username, Role role, String studentNo) {
        return "Bearer " + service.issue(username, role, studentNo).value();
    }
}
