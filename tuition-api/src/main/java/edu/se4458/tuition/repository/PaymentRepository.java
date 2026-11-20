package edu.se4458.tuition.repository;

import edu.se4458.tuition.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByTransactionId(String transactionId);
}
