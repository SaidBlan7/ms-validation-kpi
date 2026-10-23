package com.gbconnected.kpivalidation.application.manual.scope;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantAssignment;
import java.util.List;

public interface AdministerManualKpiScopeUseCase {
    List<ManualKpiPlantAssignment> list(ActorContext actor, long plantId);
    ManualKpiPlantAssignment assign(ActorContext actor, AssignManualKpiPlantCommand command, String correlationId);
    ManualKpiPlantAssignment revoke(ActorContext actor, RevokeManualKpiPlantCommand command, String correlationId);
}
