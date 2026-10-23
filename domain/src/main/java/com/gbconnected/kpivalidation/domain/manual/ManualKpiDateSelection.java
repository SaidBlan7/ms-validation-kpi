package com.gbconnected.kpivalidation.domain.manual;

import java.time.Instant;
import java.time.LocalDate;

public record ManualKpiDateSelection(
        LocalDate businessDate,
        String plantTimezone,
        boolean selectable,
        String rejectionCode,
        String message,
        Instant oeePeriodStart,
        Instant oeePeriodEnd,
        Instant oldestAllowedStart,
        Instant latestAllowedStart,
        long historicalAgeSeconds,
        boolean weekend,
        boolean calendarDayFilteringApplied) {

    public static ManualKpiDateSelection allowed(LocalDate date, String zone, Instant start, Instant end,
            Instant oldest, Instant latest, long ageSeconds) {
        return new ManualKpiDateSelection(date, zone, true, null, "Selectable", start, end, oldest, latest,
                Math.max(0, ageSeconds), isWeekend(date), false);
    }

    public static ManualKpiDateSelection rejected(LocalDate date, String zone, Instant start, Instant end,
            Instant oldest, Instant latest, String code, String message) {
        return new ManualKpiDateSelection(date, zone, false, code, message, start, end, oldest, latest,
                0L, isWeekend(date), false);
    }

    private static boolean isWeekend(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        };
    }
}
