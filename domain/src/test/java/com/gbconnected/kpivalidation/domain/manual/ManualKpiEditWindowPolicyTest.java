package com.gbconnected.kpivalidation.domain.manual;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ManualKpiEditWindowPolicyTest {
    private final ManualKpiEditWindowPolicy policy = new ManualKpiEditWindowPolicy();

    @Test void exactEightHourBoundaryIsExpired() {
        Instant created = Instant.parse("2026-10-26T12:00:00Z");
        assertTrue(policy.editableAt(created, Instant.parse("2026-10-26T19:59:59.999999999Z")));
        assertFalse(policy.editableAt(created, Instant.parse("2026-10-26T20:00:00Z")));
        assertEquals(Instant.parse("2026-10-26T20:00:00Z"), policy.expiresAt(created));
    }
}
