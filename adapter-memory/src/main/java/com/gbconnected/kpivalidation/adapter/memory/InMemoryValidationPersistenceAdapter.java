package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.port.out.ValidationPersistencePort;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryValidationPersistenceAdapter implements ValidationPersistencePort {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, OeeValidationResult> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> byKpiResult = new ConcurrentHashMap<>();

    @Override public boolean available() { return true; }
    @Override public boolean existsForKpiResult(long kpiResultId) { return byKpiResult.containsKey(kpiResultId); }

    @Override
    public synchronized OeeValidationResult create(ValidationPersistenceRequest r) {
        if (byKpiResult.containsKey(r.kpiResultId())) throw new ValidationAlreadyExistsException("This KPI result already has a validation proposal");
        long id = sequence.incrementAndGet();
        OeeValidationResult result = new OeeValidationResult(id, "PENDING", r.plantId(), r.lineId(), r.businessDate(),
                r.metricCode(), r.kpiResultId(), r.originalValue(), r.proposedValue(), r.uomId(), r.reasonType(),
                r.justification(), r.operationalCause(), r.actionPlan(), r.incidentReference(), r.proposedByUserId(),
                r.proposedAt(), r.expiresAt(), null, null, List.of());
        byId.put(id, result); byKpiResult.put(r.kpiResultId(), id); return result;
    }

    @Override public Optional<OeeValidationResult> findById(long validationId) { return Optional.ofNullable(byId.get(validationId)); }
}
