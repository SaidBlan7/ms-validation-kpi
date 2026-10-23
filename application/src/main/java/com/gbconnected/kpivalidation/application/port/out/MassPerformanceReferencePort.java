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

    default String source() { return "CANONICAL_SCHEDULING_MASS_REFERENCE"; }

    default String sourceMode() { return "SCHEDULING_CANONICAL"; }

    default String sourceMapping() {
        return "ELABORATED=batch_execution;DELIVERED=finished_product_output+packaging_conversion_to_MASS";
    }

    default String elaboratedMetricCode() { return "ELABORATED_MASS"; }

    default String deliveredMetricCode() { return "DELIVERED_MASS"; }
}
