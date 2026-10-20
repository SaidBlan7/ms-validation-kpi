package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OeeValidationResult(
        long validationId,
        String status,
        long plantId,
        Long lineId,
        LocalDate businessDate,
        String metricCode,
        Long kpiResultId,
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
        Long decidedByUserId,
        Instant decidedAt,
        List<ValidationIssue> issues) {
}
