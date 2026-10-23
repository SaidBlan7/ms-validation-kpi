package com.gbconnected.kpivalidation.application.manual;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDateSelection;
import java.time.LocalDate;

public interface ManualKpiUseCase {
    ManualKpiPolicyView policy();
    ManualKpiDateSelection dateSelection(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
    ManualKpiRecord get(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
    ManualKpiRecord save(ActorContext actor, SaveManualKpiCommand command, String correlationId);
}

