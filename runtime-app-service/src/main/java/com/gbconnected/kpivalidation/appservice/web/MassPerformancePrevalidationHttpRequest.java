package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MassPerformancePrevalidationHttpRequest(
        long plantId,
        long lineId,
        @NotNull LocalDate businessDate,
        long uomId,
        @NotNull MassPerformanceVariable variable,
        @NotNull BigDecimal originalValue,
        @NotNull BigDecimal proposedValue,
        @NotNull ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {
}
