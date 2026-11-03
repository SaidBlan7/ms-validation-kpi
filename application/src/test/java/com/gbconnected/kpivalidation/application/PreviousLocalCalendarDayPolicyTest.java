package com.gbconnected.kpivalidation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PreviousLocalCalendarDayPolicyTest {
    private final PreviousLocalCalendarDayPolicy policy = new PreviousLocalCalendarDayPolicy();

    private LocalDate eligible(String zone, String instant) {
        return policy.editableBusinessDate(new PlantContext(1L, zone, true),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void usesPlantLocalDateInsteadOfUtcOrUserTimezone() {
        assertEquals(LocalDate.of(2026, 10, 8),
                eligible("America/Mexico_City", "2026-10-10T03:00:00Z"));
        assertEquals(LocalDate.of(2026, 10, 9),
                eligible("Europe/Madrid", "2026-10-10T03:00:00Z"));
    }

    @Test
    void neverSkipsSundayOrHoliday() {
        assertEquals(LocalDate.of(2026, 10, 11),
                eligible("America/Mexico_City", "2026-10-12T18:00:00Z"));
        assertEquals(LocalDate.of(2026, 12, 25),
                eligible("America/Mexico_City", "2026-12-26T18:00:00Z"));
    }

    @Test
    void springDstTransitionStillUsesCalendarDayRatherThanRolling24Hours() {
        assertEquals(LocalDate.of(2026, 3, 7),
                eligible("America/New_York", "2026-03-09T03:30:00Z"));
        assertEquals(LocalDate.of(2026, 3, 8),
                eligible("America/New_York", "2026-03-09T04:30:00Z"));
    }

    @Test
    void fallDstTransitionStillUsesCalendarDayRatherThanRolling24Hours() {
        assertEquals(LocalDate.of(2026, 10, 31),
                eligible("America/New_York", "2026-11-02T04:30:00Z"));
        assertEquals(LocalDate.of(2026, 11, 1),
                eligible("America/New_York", "2026-11-02T05:30:00Z"));
    }
}
