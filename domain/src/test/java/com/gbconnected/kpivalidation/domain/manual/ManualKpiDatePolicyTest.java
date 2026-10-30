package com.gbconnected.kpivalidation.domain.manual;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ManualKpiDatePolicyTest {
    private final ManualKpiDatePolicy policy = new ManualKpiDatePolicy();

    @Test void exactNinetySixHourBoundaryIsInclusive() {
        ZoneId zone = ZoneId.of("UTC");
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        var result = policy.evaluate(LocalDate.of(2026, 8, 1), zone, now);
        assertTrue(result.selectable());
        assertEquals(96L * 3600L, result.historicalAgeSeconds());
    }

    @Test void oneSecondOutsideBoundaryIsRejected() {
        ZoneId zone = ZoneId.of("UTC");
        Instant now = Instant.parse("2026-08-05T00:00:01Z");
        var result = policy.evaluate(LocalDate.of(2026, 8, 1), zone, now);
        assertFalse(result.selectable());
        assertEquals("OUTSIDE_96_HOURS", result.rejectionCode());
    }

    @Test void weekendIsNotFiltered() {
        ZoneId zone = ZoneId.of("UTC");
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        var result = policy.evaluate(LocalDate.of(2026, 8, 2), zone, now);
        assertTrue(result.selectable());
        assertTrue(result.weekend());
        assertFalse(result.calendarDayFilteringApplied());
    }

    @Test void futureDateIsEvaluatedUsingPlantLocalDate() {
        ZoneId zone = ZoneId.of("America/Mexico_City");
        Instant now = Instant.parse("2026-08-05T05:30:00Z"); // Aug 4 23:30 in plant
        var result = policy.evaluate(LocalDate.of(2026, 8, 5), zone, now);
        assertFalse(result.selectable());
        assertEquals("FUTURE_DATE", result.rejectionCode());
    }

    @Test void sameUtcInstantCanBeValidForPlantLocalPreviousDate() {
        ZoneId zone = ZoneId.of("America/Mexico_City");
        Instant now = Instant.parse("2026-08-05T06:30:00Z"); // Aug 5 00:30 in plant
        var result = policy.evaluate(LocalDate.of(2026, 8, 5), zone, now);
        assertTrue(result.selectable());
    }

    @Test void oeePeriodRemainsFixedTwentyFourHoursAcrossDstSpringForward() {
        ZoneId zone = ZoneId.of("America/New_York");
        Instant now = Instant.parse("2026-03-09T12:00:00Z");
        var result = policy.evaluate(LocalDate.of(2026, 3, 8), zone, now);
        assertEquals(Duration.ofHours(24), Duration.between(result.oeePeriodStart(), result.oeePeriodEnd()));
    }
}
