package com.gbconnected.kpivalidation.adapter.memory.manual;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiAuthorizationPort;
import java.util.*;
public final class InMemoryManualKpiAuthorizationAdapter implements ManualKpiAuthorizationPort {
    private final UUID tenant; private final UUID object; private final long canonicalUserId; private final String capability; private final Set<Long> plants=new LinkedHashSet<>();
    public InMemoryManualKpiAuthorizationAdapter(UUID tenant, UUID object, long canonicalUserId, long plant, String capability){this.tenant=tenant;this.object=object;this.canonicalUserId=canonicalUserId;this.capability=capability;grant(canonicalUserId,plant);}
    public boolean configured(){return true;} public synchronized boolean authorized(UUID t,UUID o,long p){return tenant.equals(t)&&object.equals(o)&&plants.contains(p);}
    public String capabilityCode(){return capability;} public String source(){return "MEMORY_MANUAL_KPI_AUTHORIZATION";}
    public synchronized void grant(long user,long plant){if(user==canonicalUserId&&plant>0)plants.add(plant);}
    public synchronized void revoke(long user,long plant){if(user==canonicalUserId)plants.remove(plant);}
}

