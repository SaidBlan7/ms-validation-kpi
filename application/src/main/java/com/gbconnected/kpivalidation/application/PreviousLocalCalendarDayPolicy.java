package com.gbconnected.kpivalidation.application;

import java.time.*;

/**
 * Closed Spec 01/03 temporal policy: the only editable date is the previous
 * local calendar day in the authoritative plant timezone (core.plant.timezone_name).
 * Weekends and holidays are intentionally not skipped and this is not a rolling
 * elapsed-24-hour window.
 */
public final class PreviousLocalCalendarDayPolicy implements BusinessDatePolicy {
    @Override
    public LocalDate editableBusinessDate(PlantContext plant, Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of(plant.timezoneName()))).minusDays(1);
    }
}
