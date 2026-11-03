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
    @Test
    void switchingToSystemDiscardsStaleOperativeFieldsWithoutValidatingThem() {
        var normalized = new CorrectionReason(ValidationReasonType.SYSTEM, "  INC-456  ",
                "stale\u0000cause", "stale\u0001plan").normalized();
        assertEquals("INC-456", normalized.incidentNumber());
        assertNull(normalized.operationalCause());
        assertNull(normalized.actionPlan());
    }

    @Test
    void switchingToOperativeDiscardsStaleIncidentWithoutValidatingIt() {
        var normalized = new CorrectionReason(ValidationReasonType.OPERATIVE,
                "stale\u0000incident", "  Cause  ", "  Plan  ").normalized();
        assertNull(normalized.incidentNumber());
        assertEquals("Cause", normalized.operationalCause());
        assertEquals("Plan", normalized.actionPlan());
    }

    @Test
    void selectedReasonFieldsStillRejectControlCharactersAndBlanks() {
        assertThrows(DomainValidationException.class, () ->
                new CorrectionReason(ValidationReasonType.SYSTEM, "INC\u0000BAD", null, null).normalized());
        assertThrows(DomainValidationException.class, () ->
                new CorrectionReason(ValidationReasonType.OPERATIVE, null, "  ", "Plan").normalized());
    }
}
