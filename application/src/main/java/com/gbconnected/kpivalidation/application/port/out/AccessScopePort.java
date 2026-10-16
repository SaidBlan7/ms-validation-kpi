package com.gbconnected.kpivalidation.application.port.out;
import java.util.UUID;
public interface AccessScopePort { boolean hasPlantAccess(UUID tenantId, UUID objectId, long plantId); }
