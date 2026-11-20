package edu.se4458.tuition.service;

import edu.se4458.tuition.domain.Money;
import edu.se4458.tuition.domain.Payment;
import edu.se4458.tuition.domain.Tuition;
import edu.se4458.tuition.dto.PaymentRequest;
import edu.se4458.tuition.dto.PaymentResponse;
import edu.se4458.tuition.error.BusinessRuleException;
import edu.se4458.tuition.error.ConflictException;
import edu.se4458.tuition.error.NotFoundException;
import edu.se4458.tuition.repository.PaymentRepository;
import edu.se4458.tuition.repository.TuitionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pay Tuition: records a simulated partial or full payment. */
@Service
public class PaymentService {

    private final TuitionRepository tuitionRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentService(TuitionRepository tuitionRepository, PaymentRepository paymentRepository, Clock clock) {
        this.tuitionRepository = tuitionRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    /**
     * Runs in one transaction: the tuition row is locked, the payment is inserted and the paid amount is
     * increased together, so concurrent payments cannot overpay.
     */
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        String studentNo = InputValidator.studentNo(request.studentNo());
        String term = InputValidator.term(request.term());
        String transactionId = InputValidator.transactionId(request.transactionId());
        BigDecimal amount = Money.positive(request.amount(), "amount");

        if (paymentRepository.existsByTransactionId(transactionId)) {
            throw duplicate(transactionId);
        }

        Tuition tuition = tuitionRepository.findForUpdate(studentNo, term)
                .orElseThrow(() -> new NotFoundException("tuition-not-found",
                        "No tuition record for student " + studentNo + " in term " + term));

        BigDecimal balance = tuition.outstandingBalance();
        if (balance.signum() == 0) {
            throw new BusinessRuleException("tuition-fully-paid",
                    "Tuition for student " + studentNo + " in term " + term + " is already fully paid");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BusinessRuleException("payment-exceeds-balance",
                    "Payment amount " + amount.toPlainString() + " exceeds the outstanding balance "
                            + balance.toPlainString() + " " + tuition.getCurrency());
        }

        Instant now = clock.instant();
        tuition.recordPayment(amount);
        Payment payment = new Payment(tuition, transactionId, amount, now);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Another request with the same transaction ID committed between the check and the insert.
            throw duplicate(transactionId);
        }

        return new PaymentResponse(PaymentResponse.Status.SUCCESS, transactionId, studentNo, term, amount,
                tuition.getPaidAmount(), tuition.outstandingBalance(), tuition.getCurrency(), now);
    }

    private static ConflictException duplicate(String transactionId) {
        return new ConflictException("duplicate-transaction",
                "A payment with transactionId " + transactionId + " already exists");
    }
}
