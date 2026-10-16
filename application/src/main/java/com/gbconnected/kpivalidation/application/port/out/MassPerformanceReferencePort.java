package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.MassPerformanceReferenceEntry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface MassPerformanceReferencePort {
    List<MassPerformanceReferenceEntry> findObservedMasses(long plantId, long lineId,
            LocalDate businessDate, ZoneId zoneId);

    boolean uomExists(long uomId);

    boolean uomIsMassDimension(long uomId);

    default String source() { return "CANONICAL_V1_2_MASS_REFERENCE"; }

    default String sourceMode() { return "AUTO"; }

    default String sourceMapping() {
        return "DERIVED_METRIC_ELABORATED_MASS_DELIVERED_MASS_WITH_PROVISIONAL_SCHEDULING_FALLBACK";
    }

    default String elaboratedMetricCode() { return "ELABORATED_MASS"; }

    default String deliveredMetricCode() { return "DELIVERED_MASS"; }
}
