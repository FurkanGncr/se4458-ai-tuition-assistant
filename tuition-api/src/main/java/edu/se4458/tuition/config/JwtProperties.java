package edu.se4458.tuition.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code jwt.*}). Tokens are signed with HS256 using {@code secret} as raw UTF-8 bytes, the same
 * way the API gateway verifies them.
 *
 * @param secret shared HS256 secret, at least 32 bytes (from the {@code JWT_SECRET} environment variable)
 * @param issuer value of the {@code iss} claim; the gateway uses it to find the signing key
 * @param ttl    how long an access token is valid
 */
@ConfigurationProperties("jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("jwt.issuer must be set");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("jwt.ttl must be a positive duration");
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
