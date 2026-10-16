package com.gbconnected.kpivalidation.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CorrectionReasonTest {
    @Test
    void systemRequiresIncident() {
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.SYSTEM, null, null, null).validate());
    }

    @Test
    void operativeRequiresCauseAndPlan() {
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.OPERATIVE, null, "Cause", null).validate());
    }

    @Test
    void trimsSystemIncidentAndDropsStaleOperativeFields() {
        CorrectionReason normalized = new CorrectionReason(
                ValidationReasonType.SYSTEM, "  INC-123  ", "stale cause", "stale plan").normalized();
        assertEquals("INC-123", normalized.incidentNumber());
        assertNull(normalized.operationalCause());
        assertNull(normalized.actionPlan());
    }

    @Test
    void trimsOperativeEvidenceAndDropsStaleIncident() {
        CorrectionReason normalized = new CorrectionReason(
                ValidationReasonType.OPERATIVE, "INC-OLD", "  Cause  ", "  Plan  ").normalized();
        assertNull(normalized.incidentNumber());
        assertEquals("Cause", normalized.operationalCause());
        assertEquals("Plan", normalized.actionPlan());
    }

    @Test
    void rejectsWhitespaceOnlyRequiredText() {
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.OPERATIVE, null, "   ", "Plan").normalized());
    }

    @Test
    void rejectsUnsupportedControlCharacters() {
        assertThrows(DomainValidationException.class,
                () -> new CorrectionReason(ValidationReasonType.SYSTEM, "INC\u0000-1", null, null).normalized());
    }

    @Test
    void doesNotInventIncidentNumberPattern() {
        CorrectionReason normalized = new CorrectionReason(
                ValidationReasonType.SYSTEM, "ticket externo 123", null, null).normalized();
        assertEquals("ticket externo 123", normalized.incidentNumber());
    }
}
