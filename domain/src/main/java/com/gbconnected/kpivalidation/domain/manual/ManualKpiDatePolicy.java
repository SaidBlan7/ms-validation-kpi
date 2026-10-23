package com.gbconnected.kpivalidation.domain.manual;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Business policy for Spec 03. A selected business date is represented by the
 * plant-local start of that date. New captures are allowed when that instant is
 * within an inclusive rolling 96-hour window ending at "now".
 *
 * Weekends and holidays are deliberately not filtered. The OEE business period
 * is a fixed 24-hour interval from the resolved plant-local day start, even on
 * DST transition dates.
 */
public final class ManualKpiDatePolicy {
    public static final int HISTORY_HOURS = 96;
    public static final int OEE_PERIOD_HOURS = 24;

    public ManualKpiDateSelection evaluate(LocalDate businessDate, ZoneId plantZone, Instant now) {
        Objects.requireNonNull(businessDate, "businessDate is required");
        Objects.requireNonNull(plantZone, "plantZone is required");
        Objects.requireNonNull(now, "now is required");

        ZonedDateTime localNow = ZonedDateTime.ofInstant(now, plantZone);
        LocalDate currentPlantDate = localNow.toLocalDate();
        Instant periodStart = businessDate.atStartOfDay(plantZone).toInstant();
        Instant periodEnd = periodStart.plus(Duration.ofHours(OEE_PERIOD_HOURS));
        Instant oldestAllowedStart = now.minus(Duration.ofHours(HISTORY_HOURS));
        Instant latestAllowedStart = currentPlantDate.atStartOfDay(plantZone).toInstant();

        if (businessDate.isAfter(currentPlantDate)) {
            return ManualKpiDateSelection.rejected(
                    businessDate, plantZone.getId(), periodStart, periodEnd,
                    oldestAllowedStart, latestAllowedStart,
                    "FUTURE_DATE", "businessDate cannot be in the future for the selected plant");
        }
        if (periodStart.isBefore(oldestAllowedStart)) {
            return ManualKpiDateSelection.rejected(
                    businessDate, plantZone.getId(), periodStart, periodEnd,
                    oldestAllowedStart, latestAllowedStart,
                    "OUTSIDE_96_HOURS", "businessDate is outside the inclusive 96-hour historical window");
        }
        return ManualKpiDateSelection.allowed(
                businessDate, plantZone.getId(), periodStart, periodEnd,
                oldestAllowedStart, latestAllowedStart,
                Duration.between(periodStart, now).getSeconds());
    }
}
