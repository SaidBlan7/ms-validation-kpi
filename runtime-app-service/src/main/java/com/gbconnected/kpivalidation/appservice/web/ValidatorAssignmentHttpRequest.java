package com.gbconnected.kpivalidation.appservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record ValidatorAssignmentHttpRequest(
        @Positive long targetUserId,
        @Positive long plantId,
        Instant validTo,
        @NotBlank String externalApprovalReference,
        String reason) { }
