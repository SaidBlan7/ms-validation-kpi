package com.gbconnected.kpivalidation.application.port.out.manual;

import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantScope;
import java.util.List;
import java.util.UUID;

public interface ManualKpiScopePort {
    List<ManualKpiPlantScope> findEffectiveScope(UUID tenantId, UUID objectId, String featureConfigurationCode);
    String source();
}
