package edu.se4458.tuition.web;

import edu.se4458.tuition.dto.PaymentRequest;
import edu.se4458.tuition.dto.PaymentResponse;
import edu.se4458.tuition.security.StudentAccessPolicy;
import edu.se4458.tuition.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking/payments")
@Tag(name = "Banking App")
@SecurityRequirement(name = OpenApiNames.BEARER_AUTH)
public class BankingPaymentController {

    private final PaymentService paymentService;
    private final StudentAccessPolicy accessPolicy;

    public BankingPaymentController(PaymentService paymentService, StudentAccessPolicy accessPolicy) {
        this.paymentService = paymentService;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay tuition",
            description = "Requires a STUDENT token (own student number only) or a BANK token (any student). "
                    + "Records a simulated partial or full payment for one term. No real card processing. "
                    + "Rejects duplicate transaction IDs (409) and amounts above the term's balance (422).")
    @ApiResponse(responseCode = "201", description = "Payment recorded")
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Role not allowed, or a STUDENT token for another student",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No tuition record for the student and term",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate transaction ID",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Amount exceeds the balance, or the term is already paid",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public PaymentResponse pay(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody PaymentRequest request) {
        accessPolicy.checkCanAccess(token, request.studentNo());
        return paymentService.pay(request);
    }
}
