package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Create or update (upsert) the tuition of a student for a term.")
public record AddTuitionRequest(
        @Schema(description = "Unknown student numbers are created automatically (without a name).",
                example = "20230005")
        @NotBlank @Pattern(regexp = Patterns.STUDENT_NO, message = Patterns.STUDENT_NO_MESSAGE)
        String studentNo,

        @Schema(example = "2026-FALL")
        @NotBlank @Pattern(regexp = Patterns.TERM, message = Patterns.TERM_MESSAGE)
        String term,

        @Schema(description = "Tuition total. Rounded HALF_UP to 2 decimals; must be > 0 and "
                + "not lower than the amount already paid.", example = "5000.00")
        @NotNull @Positive
        BigDecimal amount,

        @Schema(description = "ISO 4217 code. Can change only while nothing has been paid for the term.",
                example = "USD")
        @NotBlank @Pattern(regexp = Patterns.CURRENCY, message = Patterns.CURRENCY_MESSAGE)
        String currency) {
}
