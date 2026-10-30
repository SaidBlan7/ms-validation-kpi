package com.gbconnected.kpivalidation.adapter.memory.manual;

import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiScopeAdministrationPort;
import com.gbconnected.kpivalidation.application.manual.scope.ManualKpiScopeConflictException;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantAssignment;
import java.time.Instant;
import java.util.*;

public final class InMemoryManualKpiScopeAdministrationAdapter implements ManualKpiScopeAdministrationPort {
    private final UUID tenant; private final UUID object; private final long actorUserId; private final String capability; private final InMemoryManualKpiAuthorizationAdapter authorization;
    private final Map<String,ManualKpiPlantAssignment> current = new LinkedHashMap<>();
    private long sequence=1000;
    public InMemoryManualKpiScopeAdministrationAdapter(UUID tenant,UUID object,long actorUserId,long initialPlant,String capability,InMemoryManualKpiAuthorizationAdapter authorization){
        this.tenant=tenant;this.object=object;this.actorUserId=actorUserId;this.capability=capability;this.authorization=Objects.requireNonNull(authorization);
        current.put(key(actorUserId,initialPlant),new ManualKpiPlantAssignment(++sequence,actorUserId,initialPlant,Instant.now(),null,actorUserId,"DEV_SEED",true));
        this.authorization.grant(actorUserId,initialPlant);
    }
    public boolean available(){return true;} public String adminCapabilityCode(){return capability;}
    public boolean canAdminister(UUID t,UUID o,long p){return tenant.equals(t)&&object.equals(o)&&p>0;}
    public synchronized List<ManualKpiPlantAssignment> list(long plantId){return current.values().stream().filter(x->x.plantId()==plantId).toList();}
    public synchronized ManualKpiPlantAssignment assign(long actor,long user,long plant,String reason,String correlation){
        String k=key(user,plant); if(current.containsKey(k)&&current.get(k).effective()) throw new ManualKpiScopeConflictException("Target user already has effective plant access");
        var x=new ManualKpiPlantAssignment(++sequence,user,plant,Instant.now(),null,actor,reason,true);current.put(k,x);authorization.grant(user,plant);return x;
    }
    public synchronized ManualKpiPlantAssignment revoke(long actor,long user,long plant,String reason,String correlation){
        String k=key(user,plant); var x=current.get(k); if(x==null||!x.effective()) throw new ManualKpiScopeConflictException("No effective plant access exists");
        var y=new ManualKpiPlantAssignment(x.assignmentId(),x.userId(),x.plantId(),x.validFrom(),Instant.now(),x.grantedByUserId(),reason,false);current.put(k,y);authorization.revoke(user,plant);return y;
    }
    private static String key(long u,long p){return u+":"+p;}
}
