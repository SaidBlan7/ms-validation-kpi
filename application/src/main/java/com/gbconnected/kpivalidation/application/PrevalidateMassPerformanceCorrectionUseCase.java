package com.gbconnected.kpivalidation.application;

public interface PrevalidateMassPerformanceCorrectionUseCase {
    MassPerformanceCorrectionPrevalidationResult prevalidateMassPerformance(
            ActorContext actor,
            PrevalidateMassPerformanceCorrectionCommand command);
}
