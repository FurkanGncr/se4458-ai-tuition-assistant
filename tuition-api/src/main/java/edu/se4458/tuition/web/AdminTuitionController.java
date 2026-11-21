package edu.se4458.tuition.web;

import edu.se4458.tuition.dto.AddTuitionRequest;
import edu.se4458.tuition.dto.AddTuitionResponse;
import edu.se4458.tuition.dto.BatchResult;
import edu.se4458.tuition.dto.Patterns;
import edu.se4458.tuition.dto.UnpaidTuitionPage;
import edu.se4458.tuition.error.InvalidInputException;
import edu.se4458.tuition.service.TuitionAdminService;
import edu.se4458.tuition.service.TuitionBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/tuition")
@Tag(name = "University Web Site - Admin")
@SecurityRequirement(name = OpenApiNames.BEARER_AUTH)
public class AdminTuitionController {

    private final TuitionAdminService adminService;
    private final TuitionBatchService batchService;

    public AdminTuitionController(TuitionAdminService adminService, TuitionBatchService batchService) {
        this.adminService = adminService;
        this.batchService = batchService;
    }

    @PostMapping
    @Operation(summary = "Add tuition",
            description = "Creates or updates (upsert) the tuition of a student for a term. There is one record per "
                    + "(studentNo, term): a repeated request updates the amount and returns 200 UPDATED; a new record "
                    + "returns 201 CREATED. The amount may not be lower than what is already paid, and the currency "
                    + "may change only while nothing has been paid (409 otherwise).")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Amount below paid amount, or currency change after payments",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not an ADMIN token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AddTuitionResponse> addTuition(@Valid @RequestBody AddTuitionRequest request) {
        AddTuitionResponse response = adminService.upsert(request);
        HttpStatus status = response.status() == AddTuitionResponse.Status.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add tuition - batch",
            description = "Uploads a CSV file (max 1 MB) with the header `studentNo,term,amount,currency`. Each row "
                    + "is processed like Add Tuition, independently: invalid rows are reported and do not stop "
                    + "the batch. Row numbers are line numbers in the file (header = line 1).")
    @ApiResponse(responseCode = "200", description = "Batch processed; see the counts and row errors")
    @ApiResponse(responseCode = "400", description = "File missing, empty, or wrong header",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not an ADMIN token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public BatchResult addTuitionBatch(
            @Parameter(description = "CSV file") @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidInputException("invalid-csv", "CSV file is empty");
        }
        return batchService.importCsv(file.getInputStream());
    }

    @GetMapping("/unpaid")
    @Operation(summary = "Unpaid tuition status",
            description = "Paged list of students with a balance greater than 0 for the term, ordered by student "
                    + "number.")
    @ApiResponse(responseCode = "200", description = "One page of unpaid tuition")
    @ApiResponse(responseCode = "400", description = "Missing or invalid term, page or pageSize",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not an ADMIN token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public UnpaidTuitionPage unpaidTuition(
            @Parameter(example = "2026-FALL")
            @RequestParam @Pattern(regexp = Patterns.TERM, message = Patterns.TERM_MESSAGE) String term,
            @Parameter(description = "1-based page number")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "Items per page, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(TuitionAdminService.MAX_PAGE_SIZE) int pageSize) {
        return adminService.listUnpaid(term, page, pageSize);
    }
}
