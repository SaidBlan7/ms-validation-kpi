package com.gbconnected.kpivalidation.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CorrectionReasonPolicyTest {
    @Test
    void enforcesConfiguredUnicodeLengths() {
        var policy = new CorrectionReasonPolicy(8, 5, 6, null);
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.OPERATIVE, null, "123456", "Plan")
                        .normalized(policy));
    }

    @Test
    void enforcesConfiguredIncidentRegexWithoutInventingOneByDefault() {
        var policy = new CorrectionReasonPolicy(20, 0, 0, "INC-[0-9]+$");
        assertEquals("INC-123", new CorrectionReason(ValidationReasonType.SYSTEM, "INC-123", null, null)
                .normalized(policy).incidentNumber());
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.SYSTEM, "ticket-123", null, null)
                        .normalized(policy));
        assertEquals("ticket-123", new CorrectionReason(ValidationReasonType.SYSTEM, "ticket-123", null, null)
                .normalized().incidentNumber());
    }
}
