package edu.se4458.tuition.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.se4458.tuition.dto.AddTuitionRequest;
import edu.se4458.tuition.dto.AddTuitionResponse;
import edu.se4458.tuition.dto.BatchResult;
import edu.se4458.tuition.dto.UnpaidTuitionItem;
import edu.se4458.tuition.dto.UnpaidTuitionPage;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.service.TuitionAdminService;
import edu.se4458.tuition.service.TuitionBatchService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import edu.se4458.tuition.config.SecurityConfig;
import edu.se4458.tuition.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTuitionController.class)
@Import({SecurityConfig.class, TestTokens.class})
class AdminTuitionControllerTest {

    private static final String URL = "/api/v1/admin/tuition";
    private static final String ADD_BODY = """
            {"studentNo":"20230005","term":"2026-FALL","amount":5000.00,"currency":"USD"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestTokens tokens;

    @MockitoBean
    private TuitionAdminService adminService;

    @MockitoBean
    private TuitionBatchService batchService;

    // ---- Add Tuition ----

    @Test
    void addTuition_returns201_whenCreated() throws Exception {
        when(adminService.upsert(any(AddTuitionRequest.class))).thenReturn(response(AddTuitionResponse.Status.CREATED));

        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.balance").value(5000.00));
    }

    @Test
    void addTuition_returns200_whenUpdated() throws Exception {
        when(adminService.upsert(any(AddTuitionRequest.class))).thenReturn(response(AddTuitionResponse.Status.UPDATED));

        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"));
    }

    @Test
    void addTuition_returns400_whenCurrencyAndTermInvalid() throws Exception {
        String body = """
                {"studentNo":"20230005","term":"2026-SUMMER","amount":5000,"currency":"usd"}
                """;

        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"))
                .andExpect(jsonPath("$.errors[1].field").value("term"));
        verifyNoInteractions(adminService);
    }

    @Test
    void addTuition_returns409_whenAmountBelowPaid() throws Exception {
        when(adminService.upsert(any(AddTuitionRequest.class))).thenThrow(new ConflictException("amount-below-paid",
                "New amount 1000.00 is lower than the amount already paid 2000.00"));

        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:amount-below-paid"));
    }

    // ---- Add Tuition - Batch ----

    @Test
    void batch_returns200_withCountsAndRowErrors() throws Exception {
        when(batchService.importCsv(any(InputStream.class))).thenReturn(new BatchResult(
                BatchResult.Status.COMPLETED_WITH_ERRORS, 3, 2, 1,
                List.of(new BatchResult.RowError(3, "term must be YYYY-FALL or YYYY-SPRING, e.g. 2026-FALL"))));
        MockMultipartFile file = csv("studentNo,term,amount,currency\n1,2026-FALL,10,USD\n");

        mvc.perform(multipart(URL + "/batch").header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3));
    }

    @Test
    void batch_returns400_whenFilePartMissing() throws Exception {
        mvc.perform(multipart(URL + "/batch").header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(batchService);
    }

    @Test
    void batch_returns400_whenFileEmpty() throws Exception {
        mvc.perform(multipart(URL + "/batch").header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .file(csv("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:invalid-csv"));
        verifyNoInteractions(batchService);
    }

    // ---- Unpaid Tuition Status ----

    @Test
    void unpaid_returnsPage_withDefaultPaging() throws Exception {
        when(adminService.listUnpaid("2026-FALL", 1, 20)).thenReturn(new UnpaidTuitionPage("2026-FALL", 1, 20, 1, 1,
                List.of(new UnpaidTuitionItem("20230003", "Zeynep Demir", new BigDecimal("6000.00"),
                        new BigDecimal("0.00"), new BigDecimal("6000.00"), "USD"))));

        mvc.perform(get(URL + "/unpaid").header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .param("term", "2026-FALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].studentNo").value("20230003"))
                .andExpect(jsonPath("$.items[0].balance").value(6000.00));
        verify(adminService).listUnpaid("2026-FALL", 1, 20);
    }

    @Test
    void unpaid_returns400_whenPageSizeTooLarge() throws Exception {
        mvc.perform(get(URL + "/unpaid").header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .param("term", "2026-FALL").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pageSize"));
        verifyNoInteractions(adminService);
    }

    @Test
    void unpaid_returns400_whenTermMissing() throws Exception {
        mvc.perform(get(URL + "/unpaid").header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(adminService);
    }

    // ---- Authentication and roles ----

    @Test
    void addTuition_returns401_whenNoToken() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ADD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:unauthorized"));
        verifyNoInteractions(adminService);
    }

    @Test
    void addTuition_returns403_forStudentToken() throws Exception {
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, tokens.student("20230001"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:forbidden"));
        verifyNoInteractions(adminService);
    }

    @Test
    void batch_returns401_whenNoToken() throws Exception {
        mvc.perform(multipart(URL + "/batch").file(csv("studentNo,term,amount,currency\n")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(batchService);
    }

    @Test
    void batch_returns403_forBankToken() throws Exception {
        mvc.perform(multipart(URL + "/batch").file(csv("studentNo,term,amount,currency\n"))
                        .header(HttpHeaders.AUTHORIZATION, tokens.bank()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(batchService);
    }

    @Test
    void unpaid_returns401_whenNoToken() throws Exception {
        mvc.perform(get(URL + "/unpaid").param("term", "2026-FALL"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminService);
    }

    @Test
    void unpaid_returns403_forStudentAndBankTokens() throws Exception {
        for (String token : new String[] {tokens.student("20230001"), tokens.bank()}) {
            mvc.perform(get(URL + "/unpaid").param("term", "2026-FALL").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(adminService);
    }

    private static AddTuitionResponse response(AddTuitionResponse.Status status) {
        return new AddTuitionResponse(status, "20230005", "2026-FALL", new BigDecimal("5000.00"),
                new BigDecimal("0.00"), new BigDecimal("5000.00"), "USD");
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "tuition.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
