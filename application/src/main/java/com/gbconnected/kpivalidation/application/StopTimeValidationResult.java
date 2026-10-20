package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StopTimeValidationResult(
        long validationId,
        String status,
        long plantId,
        Long lineId,
        LocalDate businessDate,
        long stopClassificationId,
        String classificationCode,
        String classificationName,
        String programmingType,
        long originalSeconds,
        long proposedSeconds,
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
