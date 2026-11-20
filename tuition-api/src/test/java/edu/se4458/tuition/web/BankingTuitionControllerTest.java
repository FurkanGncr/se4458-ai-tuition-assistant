package edu.se4458.tuition.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.se4458.tuition.dto.TuitionQueryResponse;
import edu.se4458.tuition.dto.TuitionQueryResponse.CurrencyTotal;
import edu.se4458.tuition.dto.TuitionQueryResponse.TermTuition;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.service.TuitionQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankingTuitionController.class)
class BankingTuitionControllerTest {

    @Autowired
    private MockMvc mvc;

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

        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2026-FALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Zeynep Demir"))
                .andExpect(jsonPath("$.terms[0].paidAmount").value(0.00))
                .andExpect(jsonPath("$.terms[0].balance").value(6000.00));
    }

    @Test
    void returns404_whenNoTuitionForTerm() throws Exception {
        when(queryService.query("20230003", "2027-SPRING")).thenThrow(new NotFoundException("tuition-not-found",
                "No tuition record for student 20230003 in term 2027-SPRING"));

        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2027-SPRING"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:tuition-not-found"));
    }

    @Test
    void returns400_whenTermIsLowercase() throws Exception {
        mvc.perform(get("/api/v1/banking/tuition/20230003").param("term", "2026-fall"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("term"));
        verifyNoInteractions(queryService);
    }
}
