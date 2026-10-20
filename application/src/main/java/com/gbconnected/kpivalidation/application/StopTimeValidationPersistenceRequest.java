package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.time.Instant;
import java.time.LocalDate;

public record StopTimeValidationPersistenceRequest(
        long plantId,
        long lineId,
        LocalDate businessDate,
        long stopClassificationId,
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
        String correlationId) {
}
