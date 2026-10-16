package com.gbconnected.kpivalidation.application;

public interface PrevalidateStopTimeCorrectionUseCase {
    StopTimeCorrectionPrevalidationResult prevalidate(ActorContext actor, PrevalidateStopTimeCorrectionCommand command);
}
