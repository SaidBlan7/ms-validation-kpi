package com.gbconnected.kpivalidation.application;

import java.time.Instant;

public record ModifyValidatorAssignmentCommand(long targetUserId, long plantId, Instant validTo,
        String externalApprovalReference, String reason) { }
