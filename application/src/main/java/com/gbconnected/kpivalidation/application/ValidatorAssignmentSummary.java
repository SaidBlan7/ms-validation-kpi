package com.gbconnected.kpivalidation.application;

import java.util.List;

public record ValidatorAssignmentSummary(
        boolean enabled,
        List<Long> plantIds,
        List<ValidatorPlantAssignmentView> plantAssignments,
        int maxValidatorsPerPlant,
        String source,
        String roleCode,
        String capabilityCode,
        List<ValidationIssue> issues) {
}
