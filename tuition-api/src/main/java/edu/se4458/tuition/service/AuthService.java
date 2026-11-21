package edu.se4458.tuition.service;

import edu.se4458.tuition.domain.AppUser;
import edu.se4458.tuition.dto.LoginRequest;
import edu.se4458.tuition.dto.LoginResponse;
import edu.se4458.tuition.error.UnauthorizedException;
import edu.se4458.tuition.repository.UserRepository;
import edu.se4458.tuition.security.JwtTokenService;
import edu.se4458.tuition.security.JwtTokenService.IssuedToken;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login: checks the BCrypt password and issues a JWT. */
@Service
public class AuthService {

    /**
     * BCrypt hash of a random value. Checked when the username is unknown so that unknown users and wrong
     * passwords take about the same time and cannot be told apart.
     */
    private static final String DUMMY_HASH = "$2a$10$el4idR7kzzLx9tq5gyja1eGoi4.dAgWYHsJfXAh3VRLj05zeTxHS2";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> user = userRepository.findByUsername(request.username());
        String hash = user.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException("invalid-credentials", "Username or password is incorrect");
        }

        AppUser u = user.get();
        IssuedToken token = tokenService.issue(u.getUsername(), u.getRole(), u.getStudentNo());
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), u.getRole(), u.getStudentNo());
    }
}
