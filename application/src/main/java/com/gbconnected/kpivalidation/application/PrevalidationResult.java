package com.gbconnected.kpivalidation.application;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record PrevalidationResult(
        boolean businessRulesValid,
        boolean readyForSubmission,
        long plantId,
        long lineId,
        LocalDate businessDate,
        String metricCode,
        BigDecimal originalValue,
        BigDecimal proposedValue,
        CorrectionJustificationView justification,
        ValidationAuditEvidenceView auditEvidence,
        String sourceRecordKey,
        String sourceState,
        String targetStatusIfPersisted,
        String persistenceState,
        List<ValidationIssue> issues) {}

