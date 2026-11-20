package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Result of a CSV batch. Each row is processed independently; invalid rows do not stop the batch.")
public record BatchResult(
        @Schema(description = "COMPLETED (all rows ok), COMPLETED_WITH_ERRORS (some failed), FAILED (all failed)",
                example = "COMPLETED_WITH_ERRORS") Status status,
        @Schema(example = "3") int totalRows,
        @Schema(example = "2") int successCount,
        @Schema(example = "1") int failureCount,
        List<RowError> errors) {

    public enum Status {
        COMPLETED, COMPLETED_WITH_ERRORS, FAILED
    }

    public record RowError(
            @Schema(description = "Line number in the file (the header is line 1)", example = "3") int row,
            @Schema(example = "term must be YYYY-FALL or YYYY-SPRING, e.g. 2026-FALL") String reason) {
    }
}
