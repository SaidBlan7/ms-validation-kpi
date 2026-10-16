package com.gbconnected.kpivalidation.application;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PrevalidateOeeCorrectionCommand(
        long plantId,
        long lineId,
        LocalDate businessDate,
        String metricCode,
        BigDecimal proposedValue,
        ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {}
