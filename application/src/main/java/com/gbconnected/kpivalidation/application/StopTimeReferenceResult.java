package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;
import java.util.List;

public record StopTimeReferenceResult(
        long plantId,
        long lineId,
        LocalDate businessDate,
        String plantTimezone,
        String sourceState,
        String programmingTypeState,
        List<StopTimeReferenceEntry> entries,
        List<ValidationIssue> issues) {
}
