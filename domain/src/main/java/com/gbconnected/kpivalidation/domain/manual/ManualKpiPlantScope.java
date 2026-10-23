package com.gbconnected.kpivalidation.domain.manual;

import java.util.List;

/** Effective plant scope exposed to KPI Manuales. Lines already include access + feature filtering. */
public record ManualKpiPlantScope(long plantId, String plantCode, String plantName, String timezoneName,
                                  List<ManualKpiLineScope> lines) {
    public ManualKpiPlantScope {
        if (plantId <= 0) throw new IllegalArgumentException("plantId must be positive");
        if (plantCode == null || plantCode.isBlank()) throw new IllegalArgumentException("plantCode is required");
        if (plantName == null || plantName.isBlank()) throw new IllegalArgumentException("plantName is required");
        if (timezoneName == null || timezoneName.isBlank()) throw new IllegalArgumentException("timezoneName is required");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
