package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record AddTuitionResponse(
        @Schema(description = "CREATED for a new (student, term) record, UPDATED for an existing one",
                example = "CREATED") Status status,
        @Schema(example = "20230005") String studentNo,
        @Schema(example = "2026-FALL") String term,
        @Schema(example = "5000.00") BigDecimal amount,
        @Schema(example = "0.00") BigDecimal paidAmount,
        @Schema(example = "5000.00") BigDecimal balance,
        @Schema(example = "USD") String currency) {

    public enum Status {
        CREATED, UPDATED
    }
}
