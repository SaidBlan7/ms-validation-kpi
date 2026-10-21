package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidationPersistencePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryValidationPersistenceAdapter implements ValidationPersistencePort {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, OeeValidationResult> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> byKpiResult = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byStopTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StopTimeValidationResult> stopById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> byMass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MassPerformanceValidationResult> massById = new ConcurrentHashMap<>();

    @Override public boolean available() { return true; }
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
    public boolean existsForMassPerformance(long lineId, LocalDate businessDate, long uomId, com.gbconnected.kpivalidation.domain.MassPerformanceVariable variable) {
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
