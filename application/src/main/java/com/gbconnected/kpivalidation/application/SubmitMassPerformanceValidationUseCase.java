package com.gbconnected.kpivalidation.application;

public interface SubmitMassPerformanceValidationUseCase {
    MassPerformanceValidationResult submitMassPerformance(ActorContext actor,
            PrevalidateMassPerformanceCorrectionCommand command, String correlationId);
}
