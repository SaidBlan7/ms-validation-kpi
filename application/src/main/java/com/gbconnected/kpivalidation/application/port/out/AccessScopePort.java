package com.gbconnected.kpivalidation.application.port.out;
import java.util.UUID;
public interface AccessScopePort {
    boolean hasPlantAccess(UUID tenantId, UUID objectId, long plantId);
    default boolean hasLineAccess(UUID tenantId, UUID objectId, long lineId) { return false; }
}
