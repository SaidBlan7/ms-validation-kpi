package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MassPerformanceValidationPersistenceRequest(
        long plantId,
        long lineId,
        LocalDate businessDate,
        long uomId,
        MassPerformanceVariable variable,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        String sourceAggregateKey,
        String sourceType,
        ValidationReasonType reasonType,
        String justification,
        String operationalCause,
        String actionPlan,
        String incidentReference,
        long proposedByUserId,
        Instant proposedAt,
        Instant expiresAt,
        String correlationId) {

    public String validationKey() {
        return "mass:line:" + lineId + ":date:" + businessDate + ":variable:" + variable.name() + ":uom:" + uomId;
    }
}
