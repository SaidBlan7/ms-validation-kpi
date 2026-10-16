package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;
import java.util.List;

public record MassPerformanceReferenceResult(
        long plantId,
        long lineId,
        LocalDate businessDate,
        String plantTimezone,
        String sourceState,
        String sourceMappingState,
        List<MassPerformanceReferenceEntry> entries,
        List<ValidationIssue> issues) {
}

