package com.gbconnected.kpivalidation.application;

public record AssignValidatorCommand(long targetUserId, long plantId,
        String externalApprovalReference, String reason) { }
