package edu.se4458.tuition.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.se4458.tuition.dto.TuitionQueryResponse;
import edu.se4458.tuition.dto.TuitionQueryResponse.CurrencyTotal;
import edu.se4458.tuition.dto.TuitionQueryResponse.TermTuition;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.service.TuitionQueryService;
import java.math.BigDecimal;
import java.util.List;
import edu.se4458.tuition.config.SecurityConfig;
import edu.se4458.tuition.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankingTuitionController.class)
@Import({SecurityConfig.class, TestTokens.class})
class BankingTuitionControllerTest {

    private static final String URL_20230001 = "/api/v1/banking/tuition/20230001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestTokens tokens;

    @MockitoBean
    private TuitionQueryService queryService;

    @Test
    void returnsTuition_forTerm() throws Exception {
        when(queryService.query("20230003", "2026-FALL")).thenReturn(new TuitionQueryResponse("20230003",
                "Zeynep Demir", "2026-FALL",
                List.of(new TermTuition("2026-FALL", new BigDecimal("6000.00"), new BigDecimal("0.00"),
                        new BigDecimal("6000.00"), "USD")),
                List.of(new CurrencyTotal("USD", new BigDecimal("6000.00"), new BigDecimal("0.00"),
                        new BigDecimal("6000.00")))));

        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2026-FALL")
                        .header(HttpHeaders.AUTHORIZATION, tokens.bank()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Zeynep Demir"))
                .andExpect(jsonPath("$.terms[0].paidAmount").value(0.00))
                .andExpect(jsonPath("$.terms[0].balance").value(6000.00));
    }

    @Test
    void returns404_whenNoTuitionForTerm() throws Exception {
        when(queryService.query("20230003", "2027-SPRING")).thenThrow(new NotFoundException("tuition-not-found",
                "No tuition record for student 20230003 in term 2027-SPRING"));

        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2027-SPRING")
                        .header(HttpHeaders.AUTHORIZATION, tokens.student("20230003")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:tuition-not-found"));
    }

    @Test
    void returns400_whenTermIsLowercase() throws Exception {
        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2026-fall")
                        .header(HttpHeaders.AUTHORIZATION, tokens.student("20230003")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("term"));
        verifyNoInteractions(queryService);
    }

    // ---- Authentication and ownership ----

    @Test
    void returns200_whenStudentQueriesOwnNumber() throws Exception {
        when(queryService.query("20230001", null)).thenReturn(new TuitionQueryResponse("20230001", "Ayse Yilmaz",
                null, List.of(), List.of()));

        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION, tokens.student("20230001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNo").value("20230001"));
    }

    @Test
    void returns403_whenStudentQueriesAnotherStudent() throws Exception {
        mvc.perform(get("/api/v1/banking/tuition/20230003").header(HttpHeaders.AUTHORIZATION,
                        tokens.student("20230001")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:forbidden"))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(queryService);
    }

    @Test
    void returns401_whenNoToken() throws Exception {
        mvc.perform(get(URL_20230001))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value(URL_20230001));
        verifyNoInteractions(queryService);
    }

    @Test
    void returns401_whenTokenExpired() throws Exception {
        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION, tokens.expiredStudent("20230001")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or expired."));
        verifyNoInteractions(queryService);
    }

    @Test
    void returns401_whenTokenSignedWithAnotherKey() throws Exception {
        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION,
                        tokens.studentSignedWithOtherKey("20230001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:unauthorized"));
        verifyNoInteractions(queryService);
    }

    @Test
    void returns401_whenTokenHasAnotherIssuer() throws Exception {
        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION, tokens.studentWithOtherIssuer("20230001")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    @Test
    void returns401_whenTokenIsGarbage() throws Exception {
        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    @Test
    void returns403_forAdminToken() throws Exception {
        mvc.perform(get(URL_20230001).header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:forbidden"));
        verifyNoInteractions(queryService);
    }
}
