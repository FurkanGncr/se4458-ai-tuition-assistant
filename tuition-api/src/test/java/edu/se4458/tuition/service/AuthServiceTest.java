package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.se4458.tuition.domain.AppUser;
import edu.se4458.tuition.domain.Role;
import edu.se4458.tuition.dto.LoginRequest;
import edu.se4458.tuition.dto.LoginResponse;
import edu.se4458.tuition.error.UnauthorizedException;
import edu.se4458.tuition.repository.UserRepository;
import edu.se4458.tuition.security.JwtTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Low cost factor keeps the test fast; production uses the default (10). */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService tokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, tokenService);
    }

    @Test
    void login_returnsToken_whenPasswordCorrect() {
        when(userRepository.findByUsername("20230001")).thenReturn(Optional.of(
                new AppUser("20230001", encoder.encode("Student123!"), Role.STUDENT, "20230001")));
        when(tokenService.issue("20230001", Role.STUDENT, "20230001"))
                .thenReturn(new JwtTokenService.IssuedToken("signed.jwt.value", 3600));

        LoginResponse response = service.login(new LoginRequest("20230001", "Student123!"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.role()).isEqualTo(Role.STUDENT);
        assertThat(response.studentNo()).isEqualTo("20230001");
    }

    @Test
    void login_throws401_whenPasswordWrong() {
        when(userRepository.findByUsername("bank")).thenReturn(Optional.of(
                new AppUser("bank", encoder.encode("Bank123!"), Role.BANK, null)));

        assertThatThrownBy(() -> service.login(new LoginRequest("bank", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Username or password is incorrect");
        verifyNoInteractions(tokenService);
    }

    @Test
    void login_throwsSame401_whenUserUnknown() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "Student123!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Username or password is incorrect");
        verifyNoInteractions(tokenService);
    }
}
