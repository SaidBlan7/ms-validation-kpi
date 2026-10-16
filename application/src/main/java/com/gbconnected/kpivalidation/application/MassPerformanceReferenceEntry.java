package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import java.math.BigDecimal;

public record MassPerformanceReferenceEntry(
        MassPerformanceVariable variable,
        long uomId,
        String uomCode,
        String uomName,
        String uomDimensionCode,
        BigDecimal originalValue,
        long sourceRecordCount,
        String sourceType,
        String sourceAggregateKey) {
}
