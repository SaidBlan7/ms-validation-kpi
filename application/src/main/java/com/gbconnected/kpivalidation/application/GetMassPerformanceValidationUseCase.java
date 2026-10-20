package com.gbconnected.kpivalidation.application;

public interface GetMassPerformanceValidationUseCase {
    MassPerformanceValidationResult getMassPerformanceValidation(ActorContext actor, long validationId);
}
