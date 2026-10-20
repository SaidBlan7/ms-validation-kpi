package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MassPerformanceValidationResult(
        long validationId,
        String status,
        long plantId,
        Long lineId,
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
        Long approvedByUserId,
        Instant approvedAt,
        List<ValidationIssue> issues) {
}
