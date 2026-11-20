package edu.se4458.tuition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** A successful simulated payment. Rejected payments are never stored. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tuition_id", nullable = false)
    private Tuition tuition;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected Payment() {
    }

    public Payment(Tuition tuition, String transactionId, BigDecimal amount, Instant paidAt) {
        this.tuition = tuition;
        this.transactionId = transactionId;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Tuition getTuition() {
        return tuition;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
