package com.gbconnected.kpivalidation.application.port.out;
import com.gbconnected.kpivalidation.application.PlantContext;
import java.util.Optional;
public interface OperationalContextPort {
    Optional<PlantContext> findPlant(long plantId);
    boolean lineBelongsToPlant(long lineId, long plantId);
}
