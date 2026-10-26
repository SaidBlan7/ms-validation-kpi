package com.gbconnected.kpivalidation.application.manual;

import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDateSelection;
import java.time.LocalDate;
import java.util.List;

public interface ManualKpiUseCase {
    ManualKpiPolicyView policy();
    ManualKpiDateSelection dateSelection(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
    List<ManualStopClassificationView> stopClassifications(ActorContext actor, long plantId, long lineId);
    List<ManualWasteCatalogEntryView> wasteCatalog(ActorContext actor, long plantId, long lineId);
    ManualKpiRecord get(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
    ManualKpiTraceView trace(ActorContext actor, long plantId, long lineId, LocalDate businessDate);
    ManualKpiRecord save(ActorContext actor, SaveManualKpiCommand command, String correlationId);
}


