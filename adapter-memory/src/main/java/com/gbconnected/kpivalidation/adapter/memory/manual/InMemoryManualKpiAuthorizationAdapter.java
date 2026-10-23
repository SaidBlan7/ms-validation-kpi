package com.gbconnected.kpivalidation.adapter.memory.manual;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiAuthorizationPort;
import java.util.UUID;
public final class InMemoryManualKpiAuthorizationAdapter implements ManualKpiAuthorizationPort {
    private final UUID tenant; private final UUID object; private final long plant; private final String capability;
    public InMemoryManualKpiAuthorizationAdapter(UUID tenant, UUID object, long plant, String capability){this.tenant=tenant;this.object=object;this.plant=plant;this.capability=capability;}
    public boolean configured(){return true;} public boolean authorized(UUID t,UUID o,long p){return tenant.equals(t)&&object.equals(o)&&plant==p;}
    public String capabilityCode(){return capability;} public String source(){return "MEMORY_MANUAL_KPI_AUTHORIZATION";}
}
