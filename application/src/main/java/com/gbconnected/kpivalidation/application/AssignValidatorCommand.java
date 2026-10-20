package com.gbconnected.kpivalidation.application;

import java.time.Instant;

public record AssignValidatorCommand(long targetUserId, long plantId, Instant validTo,
        String externalApprovalReference, String reason) { }
