package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;

public interface GetStopTimeReferenceUseCase {
    StopTimeReferenceResult get(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
}
