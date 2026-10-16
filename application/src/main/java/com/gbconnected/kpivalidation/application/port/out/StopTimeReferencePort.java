package com.gbconnected.kpivalidation.application.port.out;

import com.gbconnected.kpivalidation.application.StopTimeReferenceEntry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public interface StopTimeReferencePort {
    List<StopTimeReferenceEntry> findObservedTimes(long plantId, long lineId, LocalDate businessDate, ZoneId zoneId);
    boolean classificationExistsInPlantScope(long plantId, long stopClassificationId);

    default Optional<StopTimeReferenceEntry> findObservedTime(long plantId, long lineId, LocalDate businessDate,
            ZoneId zoneId, long stopClassificationId) {
        return findObservedTimes(plantId, lineId, businessDate, zoneId).stream()
                .filter(entry -> entry.stopClassificationId() == stopClassificationId)
                .findFirst();
    }

    default String source() { return "CANONICAL_STOPS_V1_2"; }
    default String programmingTypeState() { return "CONFIGURATION_REQUIRED"; }
    default String programmingTypeConfigurationKey() { return "GBC_STOP_PROGRAMMING_TYPE_BY_CODE"; }
}
