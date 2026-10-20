package com.gbconnected.kpivalidation.application;

public interface SubmitStopTimeValidationUseCase {
    StopTimeValidationResult submitStopTime(ActorContext actor, PrevalidateStopTimeCorrectionCommand command, String correlationId);
}
