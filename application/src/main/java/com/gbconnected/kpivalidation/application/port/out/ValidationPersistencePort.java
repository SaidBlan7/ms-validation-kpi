package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.OeeValidationResult;
import com.gbconnected.kpivalidation.application.ValidationPersistenceRequest;
import java.util.Optional;

public interface ValidationPersistencePort {
    boolean available();
    OeeValidationResult create(ValidationPersistenceRequest request);
    Optional<OeeValidationResult> findById(long validationId);
    boolean existsForKpiResult(long kpiResultId);

    static ValidationPersistencePort unavailable() {
        return new ValidationPersistencePort() {
            @Override public boolean available() { return false; }
            @Override public OeeValidationResult create(ValidationPersistenceRequest request) { throw new IllegalStateException("Validation persistence unavailable"); }
            @Override public Optional<OeeValidationResult> findById(long validationId) { return Optional.empty(); }
            @Override public boolean existsForKpiResult(long kpiResultId) { return false; }
        };
    }
}
