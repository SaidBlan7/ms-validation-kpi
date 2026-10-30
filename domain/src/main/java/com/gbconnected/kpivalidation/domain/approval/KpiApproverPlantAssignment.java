package com.gbconnected.kpivalidation.domain.approval;

import java.time.Instant;

/** Historical plant-scoped KPI Approval role assignment. */
public record KpiApproverPlantAssignment(long assignmentId, long userId, long plantId,
                                         Instant validFrom, Instant validTo, Long assignedByUserId,
                                         String reason, boolean effective) {
    public KpiApproverPlantAssignment {
        if (assignmentId <= 0 || userId <= 0 || plantId <= 0) throw new IllegalArgumentException("assignment/user/plant ids must be positive");
        if (validFrom == null) throw new IllegalArgumentException("validFrom is required");
        if (validTo != null && !validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must be after validFrom");
    }
}
