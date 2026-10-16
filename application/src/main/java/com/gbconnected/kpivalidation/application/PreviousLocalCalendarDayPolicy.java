package com.gbconnected.kpivalidation.application;

import java.time.*;

/**
 * Spec 03 policy implemented with the information currently available:
 * the only editable date is the previous local calendar day in the plant timezone.
 * Weekends and holidays are intentionally not skipped.
 * The official OEE cutoff/24-hour interpretation and authoritative timezone remain open in the spec.
 */
public final class PreviousLocalCalendarDayPolicy implements BusinessDatePolicy {
    @Override
    public LocalDate editableBusinessDate(PlantContext plant, Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of(plant.timezoneName()))).minusDays(1);
    }
}
