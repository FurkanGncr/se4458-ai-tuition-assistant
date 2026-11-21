package edu.se4458.tuition.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.se4458.tuition.config.SecurityConfig;
import edu.se4458.tuition.domain.Role;
import edu.se4458.tuition.dto.LoginRequest;
import edu.se4458.tuition.dto.LoginResponse;
import edu.se4458.tuition.error.UnauthorizedException;
import edu.se4458.tuition.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String URL = "/api/v1/auth/login";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_returns200_withToken_withoutAuthorizationHeader() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("signed.jwt.value", "Bearer", 3600, Role.STUDENT, "20230001"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"20230001\",\"password\":\"Student123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentNo").value("20230001"));
    }

    @Test
    void login_returns401_whenCredentialsWrong() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("invalid-credentials", "Username or password is incorrect"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"20230001\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:invalid-credentials"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void login_returns400_whenFieldsBlank() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[1].field").value("username"));
        verifyNoInteractions(authService);
    }
}
