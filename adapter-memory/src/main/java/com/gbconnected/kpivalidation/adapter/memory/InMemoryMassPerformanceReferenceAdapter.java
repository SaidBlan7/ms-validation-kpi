package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.MassPerformanceReferenceEntry;
import com.gbconnected.kpivalidation.application.port.out.MassPerformanceReferencePort;
import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class InMemoryMassPerformanceReferenceAdapter implements MassPerformanceReferencePort {
    @Override
    public List<MassPerformanceReferenceEntry> findObservedMasses(long plantId, long lineId,
            LocalDate businessDate, ZoneId zoneId) {
        return List.of(
                new MassPerformanceReferenceEntry(
                        MassPerformanceVariable.ELABORATED_MASS,
                        10L, "KG", "Kilogram", "MASS",
                        new BigDecimal("1250.500000"), 4L,
                        "TELEMETRY_DERIVED_METRIC",
                        aggregateKey(lineId, businessDate, MassPerformanceVariable.ELABORATED_MASS, 10L)),
                new MassPerformanceReferenceEntry(
                        MassPerformanceVariable.DELIVERED_MASS,
                        10L, "KG", "Kilogram", "MASS",
                        new BigDecimal("1210.250000"), 3L,
                        "TELEMETRY_DERIVED_METRIC",
                        aggregateKey(lineId, businessDate, MassPerformanceVariable.DELIVERED_MASS, 10L)));
    }

    @Override
    public boolean uomExists(long uomId) {
        return uomId == 10L;
    }

    @Override
    public boolean uomIsMassDimension(long uomId) {
        return uomId == 10L;
    }

    @Override
    public String source() { return "MEMORY_CANONICAL_V1_2_MASS_REFERENCE"; }

    private static String aggregateKey(long lineId, LocalDate date, MassPerformanceVariable variable, long uomId) {
        return "mass:line:" + lineId + ":date:" + date + ":variable:" + variable + ":uom:" + uomId;
    }
}
