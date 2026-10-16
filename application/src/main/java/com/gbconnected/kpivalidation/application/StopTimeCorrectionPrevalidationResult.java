package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;
import java.util.List;

public record StopTimeCorrectionPrevalidationResult(
        boolean businessRulesValid,
        boolean readyForSubmission,
        long plantId,
        long lineId,
        LocalDate businessDate,
        long stopClassificationId,
        String classificationCode,
        Boolean classificationActive,
        String programmingType,
        Long originalSeconds,
        Long observedOriginalSeconds,
        Boolean originalMatchesReference,
        Long proposedSeconds,
        String sourceAggregateKey,
        CorrectionJustificationView justification,
        ValidationAuditEvidenceView auditEvidence,
        String sourceState,
        String programmingTypeState,
        String targetStatusIfPersisted,
        String persistenceState,
        List<ValidationIssue> issues) {
}
