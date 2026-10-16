package com.gbconnected.kpivalidation.application;

public record ValidatorPlantAssignmentView(
        long plantId,
        int effectiveValidatorCount,
        int maxValidatorsPerPlant,
        int remainingSlots,
        boolean overLimit) {
}
