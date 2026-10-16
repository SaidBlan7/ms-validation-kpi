package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;
import java.util.List;

public record EligibilityResult(
        boolean eligible,
        long plantId,
        long lineId,
        LocalDate businessDate,
        LocalDate expectedBusinessDate,
        String plantTimezone,
        boolean validatorRoleConfigured,
        boolean validatorAssignmentConfigured,
        List<ValidationIssue> issues) {
}
