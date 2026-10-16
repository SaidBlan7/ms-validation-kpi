package com.gbconnected.kpivalidation.adapter.memory;
import com.gbconnected.kpivalidation.application.PlantContext;
import com.gbconnected.kpivalidation.application.port.out.*;
import java.util.*;
public final class InMemoryOperationalContextAdapter implements AccessScopePort, OperationalContextPort, DatabaseHealthPort {
    private final UUID tenantId; private final UUID objectId; private final long plantId; private final long lineId; private final String timezone;
    public InMemoryOperationalContextAdapter(UUID tenantId, UUID objectId, long plantId, long lineId, String timezone) {
        this.tenantId=tenantId; this.objectId=objectId; this.plantId=plantId; this.lineId=lineId; this.timezone=timezone;
    }
    @Override public boolean hasPlantAccess(UUID tenant, UUID object, long plant) { return tenantId.equals(tenant)&&objectId.equals(object)&&plantId==plant; }
    @Override public Optional<PlantContext> findPlant(long plant) { return plantId==plant?Optional.of(new PlantContext(plantId,timezone,true)):Optional.empty(); }
    @Override public boolean lineBelongsToPlant(long line,long plant) { return plantId==plant&&lineId==line; }
    @Override public boolean healthy() { return true; }
}
