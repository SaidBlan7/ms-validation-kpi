package com.gbconnected.kpivalidation.application;

import java.util.List;

public record StopTimePolicyView(
        String referenceSource,
        String sourceIdentityStrategy,
        String programmingTypeMappingState,
        String programmingTypeConfigurationKey,
        List<String> supportedProgrammingTypes,
        String inactiveClassificationPolicy,
        String originalValuePolicy,
        String totalConsistencyPolicy,
        String multiClassificationPolicy,
        String targetStatusIfPersisted,
        String persistenceState) {
}
