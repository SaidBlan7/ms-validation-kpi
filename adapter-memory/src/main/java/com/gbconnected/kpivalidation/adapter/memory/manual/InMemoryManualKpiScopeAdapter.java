package com.gbconnected.kpivalidation.adapter.memory.manual;

import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiScopePort;
import com.gbconnected.kpivalidation.domain.manual.*;
import java.util.*;

public final class InMemoryManualKpiScopeAdapter implements ManualKpiScopePort {
    private final UUID tenant; private final UUID object; private final long plant; private final long line; private final String timezone;
    public InMemoryManualKpiScopeAdapter(UUID tenant, UUID object, long plant, long line, String timezone) {
        this.tenant=tenant; this.object=object; this.plant=plant; this.line=line; this.timezone=timezone;
    }
    @Override public List<ManualKpiPlantScope> findEffectiveScope(UUID t,UUID o,String featureCode) {
        if (!tenant.equals(t) || !object.equals(o)) return List.of();
        return List.of(new ManualKpiPlantScope(plant,"PLANT-"+plant,"Development Plant",timezone,
                List.of(new ManualKpiLineScope(line,"LINE-"+line,"Development Line"))));
    }
    @Override public String source(){return "MEMORY_MANUAL_KPI_SCOPE";}
}
