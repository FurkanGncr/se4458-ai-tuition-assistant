package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Tuition of a student. With a term: that term only. Without: every term plus overall totals.")
public record TuitionQueryResponse(
        @Schema(example = "20230001") String studentNo,
        @Schema(example = "Ayse Yilmaz", nullable = true) String fullName,
        @Schema(description = "The requested term, or null for the overall view", example = "2026-FALL",
                nullable = true) String term,
        @Schema(description = "Tuition per term, in chronological order") List<TermTuition> terms,
        @Schema(description = "Totals per currency over the returned terms (usually one entry)")
        List<CurrencyTotal> totals) {

    public record TermTuition(
            @Schema(example = "2026-FALL") String term,
            @Schema(description = "Tuition total", example = "5000.00") BigDecimal amount,
            @Schema(example = "2000.00") BigDecimal paidAmount,
            @Schema(description = "Outstanding balance = amount - paidAmount", example = "3000.00")
            BigDecimal balance,
            @Schema(example = "USD") String currency) {
    }

    public record CurrencyTotal(
            @Schema(example = "USD") String currency,
            @Schema(example = "10000.00") BigDecimal totalAmount,
            @Schema(example = "2000.00") BigDecimal paidAmount,
            @Schema(example = "8000.00") BigDecimal balance) {
    }
}
