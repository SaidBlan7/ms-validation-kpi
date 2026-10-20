package com.gbconnected.kpivalidation.application;

public record RevokeValidatorAssignmentCommand(long targetUserId, long plantId, String reason) { }
