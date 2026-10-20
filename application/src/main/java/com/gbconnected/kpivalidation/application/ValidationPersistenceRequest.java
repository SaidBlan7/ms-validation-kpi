package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ValidationPersistenceRequest(
        long plantId,
        long lineId,
        LocalDate businessDate,
        String metricCode,
        long kpiResultId,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        Long uomId,
        ValidationReasonType reasonType,
        String justification,
        String operationalCause,
        String actionPlan,
        String incidentReference,
        long proposedByUserId,
        Instant proposedAt,
        Instant expiresAt,
        String correlationId) {
}
