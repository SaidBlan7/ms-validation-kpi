package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;

public interface GetOeeMetricReferenceUseCase {
    OeeMetricReferenceResult getOeeMetricReference(
            ActorContext actor,
            long plantId,
            long lineId,
            LocalDate businessDate,
            String metricCode);
}
