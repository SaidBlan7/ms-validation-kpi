package com.gbconnected.kpivalidation.appservice.web;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ValidatorAssignmentModifyHttpRequest(
        Instant validTo,
        @NotBlank String externalApprovalReference,
        String reason) { }
