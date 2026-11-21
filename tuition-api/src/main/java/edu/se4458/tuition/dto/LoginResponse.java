package edu.se4458.tuition.dto;

import edu.se4458.tuition.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A signed JWT access token. Send it as 'Authorization: Bearer <accessToken>'.")
public record LoginResponse(
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "3600")
        long expiresIn,

        Role role,

        @Schema(description = "The linked student number; null for BANK and ADMIN", example = "20230001")
        String studentNo) {
}
