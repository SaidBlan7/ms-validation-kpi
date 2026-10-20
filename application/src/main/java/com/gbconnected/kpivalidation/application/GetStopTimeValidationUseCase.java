package com.gbconnected.kpivalidation.application;

public interface GetStopTimeValidationUseCase {
    StopTimeValidationResult getStopTimeValidation(ActorContext actor, long validationId);
}
