package edu.se4458.tuition.web;

import edu.se4458.tuition.dto.Patterns;
import edu.se4458.tuition.dto.TuitionQueryResponse;
import edu.se4458.tuition.security.StudentAccessPolicy;
import edu.se4458.tuition.service.TuitionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking/tuition")
@Tag(name = "Banking App")
@SecurityRequirement(name = OpenApiNames.BEARER_AUTH)
public class BankingTuitionController {

    private final TuitionQueryService queryService;
    private final StudentAccessPolicy accessPolicy;

    public BankingTuitionController(TuitionQueryService queryService, StudentAccessPolicy accessPolicy) {
        this.queryService = queryService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/{studentNo}")
    @Operation(summary = "Query tuition",
            description = "Requires a STUDENT token (own student number only) or a BANK token (any student). "
                    + "With `term`: that term's total, paid amount and balance. Without `term`: every term "
                    + "plus overall totals per currency.")
    @ApiResponse(responseCode = "200", description = "Tuition found")
    @ApiResponse(responseCode = "400", description = "Invalid student number or term",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Role not allowed, or a STUDENT token for another student",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Student or tuition record not found",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public TuitionQueryResponse queryTuition(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(example = "20230001")
            @PathVariable @Pattern(regexp = Patterns.STUDENT_NO, message = Patterns.STUDENT_NO_MESSAGE)
            String studentNo,
            @Parameter(description = "Optional term; omit for the overall balance", example = "2026-FALL")
            @RequestParam(required = false) @Pattern(regexp = Patterns.TERM, message = Patterns.TERM_MESSAGE)
            String term) {
        accessPolicy.checkCanAccess(token, studentNo);
        return queryService.query(studentNo, term);
    }
}
