package com.gbconnected.kpivalidation.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Server-authoritative temporal context for Spec 03.
 * Date-time fields are ISO-8601 strings so both runtimes expose identical JSON.
 */
public record TemporalContextResult(
        long plantId,
        long lineId,
        String plantTimezone,
        LocalDate currentOperationDate,
        String currentOperationDateTime,
        String serverEvaluatedAtUtc,
        LocalDate eligibleBusinessDate,
        String eligibilityWindowStartsAt,
        String eligibilityWindowEndsAtExclusive,
        String dateSelectionMode,
        String calendarRule,
        String windowSemantics,
        boolean skipsWeekends,
        boolean skipsHolidays,
        boolean revalidateOnSubmit,
        String crossMidnightBehavior,
        String elapsed24HourRuleStatus,
        String timezoneAuthorityStatus,
        List<ValidationIssue> issues) {
}
