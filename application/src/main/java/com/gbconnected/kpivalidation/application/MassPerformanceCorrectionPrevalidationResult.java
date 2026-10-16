package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.MassPerformanceVariable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MassPerformanceCorrectionPrevalidationResult(
        boolean businessRulesValid,
        boolean readyForSubmission,
        long plantId,
        long lineId,
        LocalDate businessDate,
        long uomId,
        MassPerformanceVariable variable,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        BigDecimal observedReferenceValue,
        Boolean originalMatchesOperationalReference,
        String sourceAggregateKey,
        String sourceType,
        CorrectionJustificationView justification,
        ValidationAuditEvidenceView auditEvidence,
        String sourceState,
        String targetStatusIfPersisted,
        String persistenceState,
        List<ValidationIssue> issues) {
}
