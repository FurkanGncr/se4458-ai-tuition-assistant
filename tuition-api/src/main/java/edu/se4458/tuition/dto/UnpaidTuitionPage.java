package edu.se4458.tuition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of students with a balance > 0 for the term, ordered by student number.")
public record UnpaidTuitionPage(
        @Schema(example = "2026-FALL") String term,
        @Schema(description = "1-based page number", example = "1") int page,
        @Schema(example = "20") int pageSize,
        @Schema(example = "3") long totalItems,
        @Schema(example = "1") int totalPages,
        List<UnpaidTuitionItem> items) {
}
