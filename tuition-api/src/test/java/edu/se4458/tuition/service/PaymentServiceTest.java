package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.se4458.tuition.domain.Payment;
import edu.se4458.tuition.domain.Student;
import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.PaymentRequest;
import edu.se4458.tuition.dto.PaymentResponse;
import edu.se4458.tuition.error.BusinessRuleException;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.error.InvalidInputException;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.repository.PaymentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Mock
    private TuitionRepository tuitionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService service;
    private Tuition tuition;

    @BeforeEach
    void setUp() {
        service = new PaymentService(tuitionRepository, paymentRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        tuition = new Tuition(new Student("20230001", "Ayse Yilmaz"), "2026-FALL", new BigDecimal("5000.00"), "USD");
        tuition.recordPayment(new BigDecimal("2000.00"));
    }

    @Test
    void partialPayment_reducesBalance_andStoresPayment() {
        givenTuitionExists();

        PaymentResponse r = service.pay(request("500.00", "tx-1"));

        assertThat(r.status()).isEqualTo(PaymentResponse.Status.SUCCESS);
        assertThat(r.paidAmount()).isEqualByComparingTo("500.00");
        assertThat(r.totalPaid()).isEqualByComparingTo("2500.00");
        assertThat(r.remainingBalance()).isEqualByComparingTo("2500.00");
        assertThat(r.currency()).isEqualTo("USD");
        assertThat(r.paidAt()).isEqualTo(NOW);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTransactionId()).isEqualTo("tx-1");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getValue().getTuition()).isSameAs(tuition);
    }

    @Test
    void fullPayment_bringsBalanceToZero() {
        givenTuitionExists();

        PaymentResponse r = service.pay(request("3000.00", "tx-2"));

        assertThat(r.remainingBalance()).isEqualByComparingTo("0.00");
        assertThat(tuition.outstandingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void amountIsRoundedHalfUp() {
        givenTuitionExists();

        PaymentResponse r = service.pay(request("99.995", "tx-3"));

        assertThat(r.paidAmount()).isEqualByComparingTo("100.00");
        assertThat(r.paidAmount().scale()).isEqualTo(2);
    }

    @Test
    void amountAboveBalance_isRejectedWith422_andNothingIsSaved() {
        givenTuitionExists();

        assertThatThrownBy(() -> service.pay(request("3000.01", "tx-4")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("problemType").isEqualTo("payment-exceeds-balance");
        assertThat(tuition.getPaidAmount()).isEqualByComparingTo("2000.00");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void fullyPaidTerm_isRejectedWith422() {
        tuition.recordPayment(new BigDecimal("3000.00"));
        givenTuitionExists();

        assertThatThrownBy(() -> service.pay(request("1.00", "tx-5")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("problemType").isEqualTo("tuition-fully-paid");
    }

    @Test
    void duplicateTransactionId_isRejectedWith409_beforeLoadingTuition() {
        when(paymentRepository.existsByTransactionId("tx-dup")).thenReturn(true);

        assertThatThrownBy(() -> service.pay(request("100.00", "tx-dup")))
                .isInstanceOf(ConflictException.class)
                .extracting("problemType").isEqualTo("duplicate-transaction");
        verify(tuitionRepository, never()).findForUpdate(anyString(), anyString());
    }

    @Test
    void concurrentDuplicateOnInsert_isRejectedWith409() {
        givenTuitionExists();
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_payments_transaction_id"));

        assertThatThrownBy(() -> service.pay(request("100.00", "tx-race")))
                .isInstanceOf(ConflictException.class)
                .extracting("problemType").isEqualTo("duplicate-transaction");
    }

    @Test
    void unknownTuition_isRejectedWith404() {
        when(paymentRepository.existsByTransactionId("tx-6")).thenReturn(false);
        when(tuitionRepository.findForUpdate("20230001", "2026-FALL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(request("100.00", "tx-6")))
                .isInstanceOf(NotFoundException.class)
                .extracting("problemType").isEqualTo("tuition-not-found");
    }

    @Test
    void amountThatRoundsToZero_isRejectedWith400() {
        assertThatThrownBy(() -> service.pay(request("0.004", "tx-7")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("greater than 0");
    }

    private void givenTuitionExists() {
        when(paymentRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(tuitionRepository.findForUpdate("20230001", "2026-FALL")).thenReturn(Optional.of(tuition));
    }

    private static PaymentRequest request(String amount, String transactionId) {
        return new PaymentRequest("20230001", "2026-FALL", new BigDecimal(amount), transactionId);
    }
}
