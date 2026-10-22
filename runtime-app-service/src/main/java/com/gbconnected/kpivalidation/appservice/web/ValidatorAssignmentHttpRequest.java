package com.gbconnected.kpivalidation.appservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ValidatorAssignmentHttpRequest(
        @Positive long targetUserId,
        @Positive long plantId,
        @NotBlank String externalApprovalReference,
        String reason) { }
