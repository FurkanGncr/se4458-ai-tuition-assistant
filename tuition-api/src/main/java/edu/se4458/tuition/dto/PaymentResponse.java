package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        @Schema(example = "SUCCESS") Status status,
        @Schema(example = "6f1c2a7e-8a52-4a8e-9a43-0f0b1f8f2c11") String transactionId,
        @Schema(example = "20230001") String studentNo,
        @Schema(example = "2026-FALL") String term,
        @Schema(description = "Amount of this payment", example = "500.00") BigDecimal paidAmount,
        @Schema(description = "Total paid for the term after this payment", example = "2500.00")
        BigDecimal totalPaid,
        @Schema(description = "Balance of the term after this payment", example = "2500.00")
        BigDecimal remainingBalance,
        @Schema(example = "USD") String currency,
        Instant paidAt) {

    public enum Status {
        SUCCESS
    }
}
