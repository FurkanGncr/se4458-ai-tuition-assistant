package edu.se4458.tuition.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.se4458.tuition.dto.PaymentRequest;
import edu.se4458.tuition.dto.PaymentResponse;
import edu.se4458.tuition.error.BusinessRuleException;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankingPaymentController.class)
class BankingPaymentControllerTest {

    private static final String URL = "/api/v1/banking/payments";
    private static final String VALID_BODY = """
            {"studentNo":"20230001","term":"2026-FALL","amount":500.00,"transactionId":"tx-1"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void returns201_withRemainingBalance() throws Exception {
        when(paymentService.pay(any(PaymentRequest.class))).thenReturn(new PaymentResponse(
                PaymentResponse.Status.SUCCESS, "tx-1", "20230001", "2026-FALL", new BigDecimal("500.00"),
                new BigDecimal("2500.00"), new BigDecimal("2500.00"), "USD",
                Instant.parse("2026-11-22T10:00:00Z")));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAmount").value(500.00))
                .andExpect(jsonPath("$.remainingBalance").value(2500.00))
                .andExpect(jsonPath("$.paidAt").value("2026-11-22T10:00:00Z"));
    }

    @Test
    void returns400_whenAmountNotPositiveAndTransactionIdMissing() throws Exception {
        String body = """
                {"studentNo":"20230001","term":"2026-FALL","amount":-5}
                """;

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:validation-failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[1].field").value("transactionId"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void returns400_whenBodyIsNotJson() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(paymentService);
    }

    @Test
    void returns404_whenNoTuitionRecord() throws Exception {
        when(paymentService.pay(any(PaymentRequest.class))).thenThrow(new NotFoundException("tuition-not-found",
                "No tuition record for student 20230001 in term 2026-FALL"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:tuition-not-found"));
    }

    @Test
    void returns409_whenTransactionIdDuplicate() throws Exception {
        when(paymentService.pay(any(PaymentRequest.class))).thenThrow(new ConflictException("duplicate-transaction",
                "A payment with transactionId tx-1 already exists"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:duplicate-transaction"))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void returns422_whenAmountExceedsBalance() throws Exception {
        when(paymentService.pay(any(PaymentRequest.class))).thenThrow(new BusinessRuleException(
                "payment-exceeds-balance", "Payment amount 500.00 exceeds the outstanding balance 100.00 USD"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:tuition:problem:payment-exceeds-balance"))
                .andExpect(jsonPath("$.status").value(422));
    }
}
