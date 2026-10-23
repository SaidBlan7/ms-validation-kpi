package com.gbconnected.kpivalidation.domain.manual;

import java.time.Instant;

/** Historical canonical plant-access assignment used by KPI Manuales. */
public record ManualKpiPlantAssignment(long assignmentId, long userId, long plantId,
                                       Instant validFrom, Instant validTo, Long grantedByUserId,
                                       String reason, boolean effective) {
    public ManualKpiPlantAssignment {
        if (assignmentId <= 0 || userId <= 0 || plantId <= 0) throw new IllegalArgumentException("assignment/user/plant ids must be positive");
        if (validFrom == null) throw new IllegalArgumentException("validFrom is required");
        if (validTo != null && !validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must be after validFrom");
    }
}
