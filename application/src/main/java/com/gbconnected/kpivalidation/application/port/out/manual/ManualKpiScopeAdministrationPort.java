package com.gbconnected.kpivalidation.application.port.out.manual;

import com.gbconnected.kpivalidation.domain.manual.ManualKpiPlantAssignment;
import java.util.List;
import java.util.UUID;

public interface ManualKpiScopeAdministrationPort {
    boolean available();
    String adminCapabilityCode();
    boolean canAdminister(UUID tenantId, UUID objectId, long plantId);
    List<ManualKpiPlantAssignment> list(long plantId);
    ManualKpiPlantAssignment assign(long actorUserId, long targetUserId, long plantId, String reason, String correlationId);
    ManualKpiPlantAssignment revoke(long actorUserId, long targetUserId, long plantId, String reason, String correlationId);

    static ManualKpiScopeAdministrationPort unavailable() {
        return new ManualKpiScopeAdministrationPort() {
            public boolean available(){return false;}
            public String adminCapabilityCode(){return null;}
            public boolean canAdminister(UUID t,UUID o,long p){return false;}
            public List<ManualKpiPlantAssignment> list(long p){return List.of();}
            public ManualKpiPlantAssignment assign(long a,long u,long p,String r,String c){throw new UnsupportedOperationException("Manual KPI scope administration unavailable");}
            public ManualKpiPlantAssignment revoke(long a,long u,long p,String r,String c){throw new UnsupportedOperationException("Manual KPI scope administration unavailable");}
        };
    }
}
