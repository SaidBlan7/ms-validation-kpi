package com.gbconnected.kpivalidation.application.manual.scope;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantScope;
import java.util.List;

public interface ManualKpiScopeUseCase {
    List<ManualKpiPlantScope> myScope(ActorContext actor);
    ManualKpiScopePolicyView policy();
}
