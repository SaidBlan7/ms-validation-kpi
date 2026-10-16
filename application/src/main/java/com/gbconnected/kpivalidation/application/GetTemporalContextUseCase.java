package com.gbconnected.kpivalidation.application;

public interface GetTemporalContextUseCase {
    TemporalContextResult get(ActorContext actor, long plantId, long lineId);
}
