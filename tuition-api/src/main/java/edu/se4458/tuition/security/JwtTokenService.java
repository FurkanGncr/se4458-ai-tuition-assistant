package edu.se4458.tuition.security;

import edu.se4458.tuition.config.JwtProperties;
import edu.se4458.tuition.domain.Role;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues HS256 access tokens with the claims {@code iss}, {@code sub} (username), {@code role}, {@code studentNo}
 * (STUDENT only), {@code iat} and {@code exp}.
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(String username, Role role, String studentNo) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(TokenClaims.ROLE, role.name());
        if (studentNo != null) {
            claims.claim(TokenClaims.STUDENT_NO, studentNo);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, properties.ttl().toSeconds());
    }

    /** A signed token and its lifetime in seconds. */
    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
