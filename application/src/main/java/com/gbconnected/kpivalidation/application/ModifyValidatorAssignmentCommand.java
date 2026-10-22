package com.gbconnected.kpivalidation.application;

public record ModifyValidatorAssignmentCommand(long targetUserId, long plantId,
        String externalApprovalReference, String reason) { }
