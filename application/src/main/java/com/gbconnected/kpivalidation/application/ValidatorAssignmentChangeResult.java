package com.gbconnected.kpivalidation.application;

import java.time.Instant;

public record ValidatorAssignmentChangeResult(String action, long assignmentId, long targetUserId,
        long plantId, Instant validFrom, Instant validTo, long changedByUserId, Instant changedAt,
        int effectiveValidatorCount, int maxValidatorsPerPlant) { }
