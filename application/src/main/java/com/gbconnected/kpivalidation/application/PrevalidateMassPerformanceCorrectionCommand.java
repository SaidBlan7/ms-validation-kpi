package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrevalidateMassPerformanceCorrectionCommand(
        long plantId,
        long lineId,
        LocalDate businessDate,
        long uomId,
        MassPerformanceVariable variable,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {
}
