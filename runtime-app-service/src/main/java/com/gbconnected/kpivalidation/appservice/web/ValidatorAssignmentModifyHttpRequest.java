package com.gbconnected.kpivalidation.appservice.web;

import jakarta.validation.constraints.NotBlank;

public record ValidatorAssignmentModifyHttpRequest(
        @NotBlank String externalApprovalReference,
        String reason) { }
