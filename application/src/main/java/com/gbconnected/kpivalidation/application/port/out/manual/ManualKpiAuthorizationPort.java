package com.gbconnected.kpivalidation.application.port.out.manual;
import java.util.UUID;
public interface ManualKpiAuthorizationPort {
    boolean configured();
    boolean authorized(UUID tenantId, UUID objectId, long plantId);
    String capabilityCode();
    String source();
}
