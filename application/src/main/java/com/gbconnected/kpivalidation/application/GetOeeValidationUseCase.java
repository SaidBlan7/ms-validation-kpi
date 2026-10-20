package com.gbconnected.kpivalidation.application;
public interface GetOeeValidationUseCase {
    OeeValidationResult get(ActorContext actor, long validationId);
}
