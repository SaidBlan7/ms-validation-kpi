package com.gbconnected.kpivalidation.domain.manual;

/** Effective line that an actor may use from KPI Manuales. */
public record ManualKpiLineScope(long lineId, String lineCode, String lineName) {
    public ManualKpiLineScope {
        if (lineId <= 0) throw new IllegalArgumentException("lineId must be positive");
        if (lineCode == null || lineCode.isBlank()) throw new IllegalArgumentException("lineCode is required");
        if (lineName == null || lineName.isBlank()) throw new IllegalArgumentException("lineName is required");
    }
}
