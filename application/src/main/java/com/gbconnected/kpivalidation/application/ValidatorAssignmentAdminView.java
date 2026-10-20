package com.gbconnected.kpivalidation.application;

import java.time.Instant;

public record ValidatorAssignmentAdminView(long assignmentId, long targetUserId, long plantId,
        String targetDisplayName, String targetEmail, Instant validFrom, Instant validTo,
        Long assignedByUserId, String reason, boolean effective) { }
