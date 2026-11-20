package edu.se4458.tuition.web;

import static org.mockito.Mockito.verify;
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

@WebMvcTest(MobileTuitionController.class)
class MobileTuitionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TuitionQueryService queryService;

    @Test
    void returnsAllTermsAndTotals_whenNoTermGiven() throws Exception {
        when(queryService.query("20230001", null)).thenReturn(new TuitionQueryResponse("20230001", "Ayse Yilmaz",
                null,
                List.of(new TermTuition("2026-FALL", bd("5000.00"), bd("2000.00"), bd("3000.00"), "USD"),
                        new TermTuition("2027-SPRING", bd("5000.00"), bd("0.00"), bd("5000.00"), "USD")),
                List.of(new CurrencyTotal("USD", bd("10000.00"), bd("2000.00"), bd("8000.00")))));

        mvc.perform(get("/api/v1/mobile/tuition/20230001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.studentNo").value("20230001"))
                .andExpect(jsonPath("$.term").doesNotExist())
                .andExpect(jsonPath("$.terms.length()").value(2))
                .andExpect(jsonPath("$.terms[0].balance").value(3000.00))
                .andExpect(jsonPath("$.totals[0].currency").value("USD"))
                .andExpect(jsonPath("$.totals[0].balance").value(8000.00));
    }

    @Test
    void returnsOneTerm_whenTermGiven() throws Exception {
        when(queryService.query("20230001", "2026-FALL")).thenReturn(new TuitionQueryResponse("20230001",
                "Ayse Yilmaz", "2026-FALL",
                List.of(new TermTuition("2026-FALL", bd("5000.00"), bd("2000.00"), bd("3000.00"), "USD")),
                List.of(new CurrencyTotal("USD", bd("5000.00"), bd("2000.00"), bd("3000.00")))));

        mvc.perform(get("/api/v1/mobile/tuition/20230001").param("term", "2026-FALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term").value("2026-FALL"))
                .andExpect(jsonPath("$.terms.length()").value(1))
                .andExpect(jsonPath("$.terms[0].amount").value(5000.00));
        verify(queryService).query("20230001", "2026-FALL");
    }

    @Test
    void returns404ProblemDetail_whenStudentUnknown() throws Exception {
        when(queryService.query("99999999", null))
                .thenThrow(new NotFoundException("student-not-found", "Student 99999999 was not found"));

        mvc.perform(get("/api/v1/mobile/tuition/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:student-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Student 99999999 was not found"));
    }

    @Test
    void returns400_whenTermFormatInvalid() throws Exception {
        mvc.perform(get("/api/v1/mobile/tuition/20230001").param("term", "2026-SUMMER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("term"));
        verifyNoInteractions(queryService);
    }

    @Test
    void returns400_whenStudentNoHasInvalidCharacters() throws Exception {
        mvc.perform(get("/api/v1/mobile/tuition/2023-0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("studentNo"));
        verifyNoInteractions(queryService);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
