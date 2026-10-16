package com.gbconnected.kpivalidation.application;

import java.util.List;

public record MassPerformancePolicyView(
        String sourceState,
        String sourceMode,
        String sourceIdentityStrategy,
        List<String> supportedVariables,
        String elaboratedMetricCode,
        String deliveredMetricCode,
        String fallbackMappingState,
        String originalValuePolicy,
        String absentOriginalPolicy,
        String uomPolicy,
        String precisionPolicy,
        String relationPolicy,
        String lateAutomaticDataPolicy,
        String targetStatusIfPersisted,
        String persistenceState) {
}
