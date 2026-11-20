package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Simulated tuition payment. No real card processing.")
public record PaymentRequest(
        @Schema(example = "20230001")
        @NotBlank @Pattern(regexp = Patterns.STUDENT_NO, message = Patterns.STUDENT_NO_MESSAGE)
        String studentNo,

        @Schema(example = "2026-FALL")
        @NotBlank @Pattern(regexp = Patterns.TERM, message = Patterns.TERM_MESSAGE)
        String term,

        @Schema(description = "Amount in the tuition's currency. Rounded HALF_UP to 2 decimals; "
                + "must be > 0 and not exceed the term's balance.", example = "500.00")
        @NotNull @Positive
        BigDecimal amount,

        @Schema(description = "Client-generated idempotency key (e.g. a UUID). A repeated ID is rejected with 409.",
                example = "6f1c2a7e-8a52-4a8e-9a43-0f0b1f8f2c11")
        @NotBlank @Size(max = Patterns.TRANSACTION_ID_MAX)
        String transactionId) {
}
