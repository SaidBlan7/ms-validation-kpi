package com.gbconnected.kpivalidation.application;
public interface SubmitOeeValidationUseCase {
    OeeValidationResult submit(ActorContext actor, PrevalidateOeeCorrectionCommand command, String correlationId);
}
