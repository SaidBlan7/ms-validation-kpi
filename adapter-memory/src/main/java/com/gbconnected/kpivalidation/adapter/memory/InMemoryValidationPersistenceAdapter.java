package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidationPersistencePort;
import com.gbconnected.kpivalidation.application.approval.*;
import com.gbconnected.kpivalidation.application.port.out.approval.KpiApprovalPersistencePort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryValidationPersistenceAdapter implements ValidationPersistencePort, KpiApprovalPersistencePort {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, OeeValidationResult> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> byKpiResult = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byStopTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StopTimeValidationResult> stopById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byMass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MassPerformanceValidationResult> massById = new ConcurrentHashMap<>();

    @Override public boolean available() { return true; }
    @Override public boolean supportsMassPerformancePersistence() { return true; }
    @Override public boolean existsForKpiResult(long kpiResultId) { return byKpiResult.containsKey(kpiResultId); }

    @Override
    public synchronized OeeValidationResult create(ValidationPersistenceRequest r) {
        if (byKpiResult.containsKey(r.kpiResultId())) throw new ValidationAlreadyExistsException("This KPI result already has a validation proposal");
        long id = sequence.incrementAndGet();
        OeeValidationResult result = new OeeValidationResult(id, "PENDING", r.plantId(), r.lineId(), r.businessDate(),
                r.metricCode(), r.kpiResultId(), r.originalValue(), r.proposedValue(), r.uomId(), r.reasonType(),
                r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                r.proposedAt(), r.expiresAt(), null, null, audit(r.proposedByUserId(), r.proposedAt()), List.of());
        byId.put(id, result); byKpiResult.put(r.kpiResultId(), id); return result;
    }

    @Override public Optional<OeeValidationResult> findById(long validationId) { return Optional.ofNullable(byId.get(validationId)); }


    @Override
    public boolean existsForStopTime(long lineId, LocalDate businessDate, long stopClassificationId) {
        return byStopTime.containsKey(stopKey(lineId, businessDate, stopClassificationId));
    }

    @Override
    public synchronized StopTimeValidationResult createStopTime(StopTimeValidationPersistenceRequest r) {
        String key = stopKey(r.lineId(), r.businessDate(), r.stopClassificationId());
        if (byStopTime.containsKey(key)) {
            throw new ValidationAlreadyExistsException("This stop classification already has a validation proposal for the line/date");
        }
        long id = sequence.incrementAndGet();
        StopTimeValidationResult result = new StopTimeValidationResult(
                id, "PENDING", r.plantId(), r.lineId(), r.businessDate(), r.stopClassificationId(),
                "STOP_" + r.stopClassificationId(), "Stop classification " + r.stopClassificationId(), null,
                r.originalSeconds(), r.proposedSeconds(), r.reasonType(), r.justification(),
                r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                r.proposedAt(), r.expiresAt(), null, null, audit(r.proposedByUserId(), r.proposedAt()), List.of());
        stopById.put(id, result);
        byStopTime.put(key, id);
        return result;
    }

    @Override
    public Optional<StopTimeValidationResult> findStopTimeById(long validationId) {
        return Optional.ofNullable(stopById.get(validationId));
    }

    @Override
    public boolean existsForMassPerformance(long lineId, LocalDate businessDate, long uomId, String metricCode) {
        if (metricCode == null || metricCode.isBlank()) return false;
        com.gbconnected.kpivalidation.domain.MassPerformanceVariable variable =
                com.gbconnected.kpivalidation.domain.MassPerformanceVariable.valueOf(metricCode);
        return byMass.containsKey(massKey(lineId, businessDate, uomId, variable));
    }

    @Override
    public synchronized MassPerformanceValidationResult createMassPerformance(MassPerformanceValidationPersistenceRequest r) {
        String key = massKey(r.lineId(), r.businessDate(), r.uomId(), r.variable());
        if (byMass.containsKey(key)) {
            throw new ValidationAlreadyExistsException("This mass performance value already has a validation proposal");
        }
        long id = sequence.incrementAndGet();
        MassPerformanceValidationResult result = new MassPerformanceValidationResult(
                id, "PENDING", r.plantId(), r.lineId(), r.businessDate(), r.uomId(), r.variable(),
                r.originalValue(), r.proposedValue(), r.sourceAggregateKey(), r.sourceType(), r.reasonType(),
                r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                r.proposedAt(), r.expiresAt(), null, null, audit(r.proposedByUserId(), r.proposedAt()), List.of());
        massById.put(id, result);
        byMass.put(key, id);
        return result;
    }

    @Override
    public Optional<MassPerformanceValidationResult> findMassPerformanceById(long validationId) {
        return Optional.ofNullable(massById.get(validationId));
    }

    @Override
    public List<ApprovalValidationView> pending() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(byId.values().stream().map(this::approval),
                                stopById.values().stream().map(this::approval)),
                        massById.values().stream().map(this::approval))
                .filter(v -> "PENDING".equals(v.status()))
                .sorted(java.util.Comparator.comparing(ApprovalValidationView::proposedAt).reversed())
                .toList();
    }

    @Override
    public List<ApprovalValidationView> all() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(byId.values().stream().map(this::approval),
                                stopById.values().stream().map(this::approval)),
                        massById.values().stream().map(this::approval))
                .sorted(java.util.Comparator.comparing(ApprovalValidationView::proposedAt).reversed())
                .toList();
    }

    @Override
    public Optional<ApprovalValidationView> find(long validationId) {
        if (byId.containsKey(validationId)) return Optional.of(approval(byId.get(validationId)));
        if (stopById.containsKey(validationId)) return Optional.of(approval(stopById.get(validationId)));
        if (massById.containsKey(validationId)) return Optional.of(approval(massById.get(validationId)));
        return Optional.empty();
    }

    @Override
    public synchronized ApprovalValidationView decide(long validationId, long userId, ApprovalDecision decision, Instant at, String correlationId) {
        if (byId.containsKey(validationId)) {
            OeeValidationResult r = byId.get(validationId);
            ensurePending(r.status(), r.expiresAt(), at);
            r = new OeeValidationResult(r.validationId(), decision.name(), r.plantId(), r.lineId(), r.businessDate(),
                    r.metricCode(), r.kpiResultId(), r.originalValue(), r.proposedValue(), r.uomId(), r.reasonType(),
                    r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                    r.proposedAt(), r.expiresAt(), userId, at, r.auditEvidence(), r.issues());
            byId.put(validationId, r);
            return approval(r);
        }
        if (stopById.containsKey(validationId)) {
            StopTimeValidationResult r = stopById.get(validationId);
            ensurePending(r.status(), r.expiresAt(), at);
            r = new StopTimeValidationResult(r.validationId(), decision.name(), r.plantId(), r.lineId(), r.businessDate(),
                    r.stopClassificationId(), r.classificationCode(), r.classificationName(), r.programmingType(),
                    r.originalSeconds(), r.proposedSeconds(), r.reasonType(), r.justification(), r.operationalCause(),
                    r.actionPlan(), r.incidentReference(), r.proposedByUserId(), r.proposedAt(), r.expiresAt(), userId, at,
                    r.auditEvidence(), r.issues());
            stopById.put(validationId, r);
            return approval(r);
        }
        if (massById.containsKey(validationId)) {
            MassPerformanceValidationResult r = massById.get(validationId);
            ensurePending(r.status(), r.expiresAt(), at);
            r = new MassPerformanceValidationResult(r.validationId(), decision.name(), r.plantId(), r.lineId(),
                    r.businessDate(), r.uomId(), r.variable(), r.originalValue(), r.proposedValue(), r.sourceAggregateKey(),
                    r.sourceType(), r.reasonType(), r.justification(), r.operationalCause(), r.actionPlan(),
                    r.incidentReference(), r.proposedByUserId(), r.proposedAt(), r.expiresAt(), userId, at,
                    r.auditEvidence(), r.issues());
            massById.put(validationId, r);
            return approval(r);
        }
        throw new ApprovalNotFoundException("KPI validation not found");
    }

    @Override
    public synchronized void expirePending(Instant now, String correlationId) {
        byId.replaceAll((k, r) -> "PENDING".equals(r.status()) && r.expiresAt().isBefore(now)
                ? new OeeValidationResult(r.validationId(), "EXPIRED", r.plantId(), r.lineId(), r.businessDate(),
                        r.metricCode(), r.kpiResultId(), r.originalValue(), r.proposedValue(), r.uomId(), r.reasonType(),
                        r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                        r.proposedAt(), r.expiresAt(), null, null, r.auditEvidence(), r.issues())
                : r);
        stopById.replaceAll((k, r) -> "PENDING".equals(r.status()) && r.expiresAt().isBefore(now)
                ? new StopTimeValidationResult(r.validationId(), "EXPIRED", r.plantId(), r.lineId(), r.businessDate(),
                        r.stopClassificationId(), r.classificationCode(), r.classificationName(), r.programmingType(),
                        r.originalSeconds(), r.proposedSeconds(), r.reasonType(), r.justification(), r.operationalCause(),
                        r.actionPlan(), r.incidentReference(), r.proposedByUserId(), r.proposedAt(), r.expiresAt(), null, null,
                        r.auditEvidence(), r.issues())
                : r);
        massById.replaceAll((k, r) -> "PENDING".equals(r.status()) && r.expiresAt().isBefore(now)
                ? new MassPerformanceValidationResult(r.validationId(), "EXPIRED", r.plantId(), r.lineId(), r.businessDate(),
                        r.uomId(), r.variable(), r.originalValue(), r.proposedValue(), r.sourceAggregateKey(), r.sourceType(),
                        r.reasonType(), r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(),
                        r.proposedByUserId(), r.proposedAt(), r.expiresAt(), null, null, r.auditEvidence(), r.issues())
                : r);
    }

    @Override public boolean supportsWasteValidations() { return false; }

    private static void ensurePending(String status, Instant expiresAt, Instant decisionAt) {
        if (!"PENDING".equals(status) || decisionAt.isAfter(expiresAt)) {
            throw new ApprovalConflictException("Validation was already decided or expired");
        }
    }

    private ApprovalValidationView approval(OeeValidationResult r) {
        String proposer = r.auditEvidence() == null ? null : r.auditEvidence().displayNameSnapshot();
        return new ApprovalValidationView(r.validationId(), "OEE", "KPI_RESULT", r.status(), r.plantId(), r.lineId(),
                r.businessDate(), r.reasonType().name(), r.justification(), r.operationalCause(), r.actionPlan(),
                r.incidentReference(), r.proposedByUserId(), proposer, r.proposedAt(), r.expiresAt(), r.decidedByUserId(),
                null, r.decidedAt(), "PENDING".equals(r.status()),
                List.of(new ApprovalValueChangeView(r.kpiResultId(), r.metricCode(), r.metricCode(), null, null, null, null,
                        null, null, null, null, r.originalValue(), r.proposedValue(), r.uomId(), null, null)));
    }

    private ApprovalValidationView approval(StopTimeValidationResult r) {
        String proposer = r.auditEvidence() == null ? null : r.auditEvidence().displayNameSnapshot();
        return new ApprovalValidationView(r.validationId(), "OEE", "STOP_TIME", r.status(), r.plantId(), r.lineId(),
                r.businessDate(), r.reasonType().name(), r.justification(), r.operationalCause(), r.actionPlan(),
                r.incidentReference(), r.proposedByUserId(), proposer, r.proposedAt(), r.expiresAt(), r.decidedByUserId(),
                null, r.decidedAt(), "PENDING".equals(r.status()),
                List.of(new ApprovalValueChangeView(null, null, null, r.stopClassificationId(), r.classificationCode(),
                        r.classificationName(), r.programmingType(), null, null, null, null,
                        java.math.BigDecimal.valueOf(r.originalSeconds()), java.math.BigDecimal.valueOf(r.proposedSeconds()), null, "SECOND", "Seconds")));
    }

    private ApprovalValidationView approval(MassPerformanceValidationResult r) {
        String proposer = r.auditEvidence() == null ? null : r.auditEvidence().displayNameSnapshot();
        String metricCode = r.variable().name();
        return new ApprovalValidationView(r.validationId(), "OEE", "MASS_PERFORMANCE", r.status(), r.plantId(), r.lineId(),
                r.businessDate(), r.reasonType().name(), r.justification(), r.operationalCause(), r.actionPlan(),
                r.incidentReference(), r.proposedByUserId(), proposer, r.proposedAt(), r.expiresAt(), r.approvedByUserId(),
                null, r.approvedAt(), "PENDING".equals(r.status()),
                List.of(new ApprovalValueChangeView(null, null, null, null, null, null, null, null, metricCode, metricCode, "OEE",
                        r.originalValue(), r.proposedValue(), r.uomId(), null, null)));
    }

    private static ValidationAuditEvidenceView audit(long userId, java.time.Instant at) {
        return new ValidationAuditEvidenceView(
                userId, null, null, null, null, null, true,
                "IN_MEMORY_CANONICAL_ID_ONLY", at, "ABSOLUTE_INSTANT_UTC",
                "IN_MEMORY_VALIDATION_PERSISTED");
    }

    private static String massKey(long lineId, LocalDate businessDate, long uomId, com.gbconnected.kpivalidation.domain.MassPerformanceVariable variable) {
        return lineId + ":" + businessDate + ":" + variable.name() + ":" + uomId;
    }

    private static String stopKey(long lineId, LocalDate businessDate, long stopClassificationId) {
        return lineId + ":" + businessDate + ":" + stopClassificationId;
    }

}
