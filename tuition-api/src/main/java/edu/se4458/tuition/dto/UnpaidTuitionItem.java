package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record UnpaidTuitionItem(
        @Schema(example = "20230003") String studentNo,
        @Schema(example = "Zeynep Demir", nullable = true) String fullName,
        @Schema(example = "6000.00") BigDecimal amount,
        @Schema(example = "0.00") BigDecimal paidAmount,
        @Schema(example = "6000.00") BigDecimal balance,
        @Schema(example = "USD") String currency) {
}
