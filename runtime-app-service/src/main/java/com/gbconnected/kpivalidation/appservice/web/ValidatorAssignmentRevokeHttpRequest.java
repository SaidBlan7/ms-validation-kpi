package com.gbconnected.kpivalidation.appservice.web;

import jakarta.validation.constraints.NotBlank;

public record ValidatorAssignmentRevokeHttpRequest(@NotBlank String reason) { }
