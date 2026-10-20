package com.gbconnected.kpivalidation.application;

import java.util.List;

public interface AdministerValidatorAssignmentsUseCase {
    List<ValidatorAssignmentAdminView> list(ActorContext actor, long plantId);
    ValidatorAssignmentChangeResult assign(ActorContext actor, AssignValidatorCommand command, String correlationId);
    ValidatorAssignmentChangeResult modify(ActorContext actor, ModifyValidatorAssignmentCommand command, String correlationId);
    ValidatorAssignmentChangeResult revoke(ActorContext actor, RevokeValidatorAssignmentCommand command, String correlationId);
}
