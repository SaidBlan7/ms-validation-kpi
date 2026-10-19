package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;

public interface GetMassPerformanceReferenceUseCase {
    MassPerformanceReferenceResult getMassPerformanceReference(
            ActorContext actor,
            long plantId,
            long lineId,
            LocalDate businessDate);
}
