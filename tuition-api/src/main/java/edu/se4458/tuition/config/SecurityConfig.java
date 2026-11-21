package edu.se4458.tuition.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.se4458.tuition.domain.Role;
import edu.se4458.tuition.security.ProblemDetailsAuthHandlers;
import edu.se4458.tuition.security.StudentAccessPolicy;
import edu.se4458.tuition.security.TokenClaims;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT authentication and role rules.
 *
 * <ul>
 *   <li>Open: login, mobile Query Tuition (rate-limited at the gateway), Swagger UI.</li>
 *   <li>Banking: STUDENT or BANK. Ownership (a student only sees their own number) is checked in the
 *       controllers with {@link StudentAccessPolicy}.</li>
 *   <li>Admin: ADMIN only.</li>
 * </ul>
 * The gateway validates the same token first; this is the second check (defense in depth).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailsAuthHandlers authHandlers)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/mobile/tuition/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/banking/**").hasAnyRole(Role.STUDENT.name(), Role.BANK.name())
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Checks exp/nbf (with the default 60 s clock skew) and that iss matches.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public StudentAccessPolicy studentAccessPolicy() {
        return new StudentAccessPolicy();
    }

    @Bean
    public ProblemDetailsAuthHandlers problemDetailsAuthHandlers(JsonMapper jsonMapper) {
        return new ProblemDetailsAuthHandlers(jsonMapper);
    }

    /** Maps the {@code role} claim to a Spring authority, e.g. STUDENT to ROLE_STUDENT. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
    }
}
