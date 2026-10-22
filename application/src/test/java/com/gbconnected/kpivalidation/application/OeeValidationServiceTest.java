package com.gbconnected.kpivalidation.application;

import static org.junit.jupiter.api.Assertions.*;

import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OeeValidationServiceTest {
    private static final UUID T = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID O = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ZONE = "America/Mexico_City";

    private OeeValidationService service(boolean specificallyAssigned) {
        return service(specificallyAssigned, Clock.fixed(Instant.parse("2026-10-09T18:00:00Z"), ZoneOffset.UTC));
    }

    private OeeValidationService service(boolean specificallyAssigned, Clock clock) {
        AccessScopePort access = (tenant, object, plant) -> plant == 1;
        OperationalContextPort context = new OperationalContextPort() {
            public Optional<PlantContext> findPlant(long plant) {
                return plant == 1 ? Optional.of(new PlantContext(1, ZONE, true)) : Optional.empty();
            }
            public boolean lineBelongsToPlant(long line, long plant) { return line == 10 && plant == 1; }
        };
        ValidatorAssignmentPort assignments = new ValidatorAssignmentPort() {
            public boolean configured() { return true; }
            public boolean isAssigned(UUID tenant, UUID object, long plant) {
                return specificallyAssigned && T.equals(tenant) && O.equals(object) && plant == 1;
            }
            public Set<Long> assignedPlants(UUID tenant, UUID object) {
                return isAssigned(tenant, object, 1) ? Set.of(1L) : Set.of();
            }
            public int assignedValidatorCount(long plant) { return specificallyAssigned && plant == 1 ? 1 : 0; }
            public String source() { return "test"; }
        };
        StopTimeReferencePort stopTimes = new StopTimeReferencePort() {
            public List<StopTimeReferenceEntry> findObservedTimes(long plant, long line, LocalDate date, ZoneId zoneId) {
                return List.of(new StopTimeReferenceEntry(100L, "MECHANICAL", "Mechanical stop", true, 600L, 1L, 1L,
                        "UNPROGRAMMED", "stops:line:10:date:" + date + ":classification:100"));
            }
            public boolean classificationExistsInPlantScope(long plant, long classificationId) {
                return plant == 1 && classificationId == 100L;
            }
            public String source() { return "CANONICAL_STOPS_V1_2_TEST"; }
            public String programmingTypeState() { return "CONFIGURED_BY_CLASSIFICATION_CODE"; }
        };
        MassPerformanceReferencePort masses = new MassPerformanceReferencePort() {
            public List<MassPerformanceReferenceEntry> findObservedMasses(long plant, long line, LocalDate date, ZoneId zoneId) {
                return List.of(
                        new MassPerformanceReferenceEntry(com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS,
                                10L, "KG", "Kilogram", "MASS", new BigDecimal("1250.5"), 4L,
                                "TELEMETRY_DERIVED_METRIC", "mass:line:10:date:" + date + ":variable:ELABORATED_MASS:uom:10"),
                        new MassPerformanceReferenceEntry(com.gbconnected.kpivalidation.domain.MassPerformanceVariable.DELIVERED_MASS,
                                10L, "KG", "Kilogram", "MASS", new BigDecimal("1210.25"), 3L,
                                "TELEMETRY_DERIVED_METRIC", "mass:line:10:date:" + date + ":variable:DELIVERED_MASS:uom:10"));
            }
            public boolean uomExists(long uomId) { return uomId == 10L; }
            public boolean uomIsMassDimension(long uomId) { return uomId == 10L; }
            public String source() { return "test-mass"; }
        };
        OeeMetricSourcePort oeeMetrics = new OeeMetricSourcePort() {
            public Optional<OeeMetricSnapshot> findMetric(long plant, long line, LocalDate date, ZoneId zoneId, String metricCode) {
                if (plant == 1 && line == 10 && "OEE".equals(metricCode)) {
                    return Optional.of(new OeeMetricSnapshot(plant, line, date, metricCode,
                            new BigDecimal("86.25"), "kpi.kpi_result:77"));
                }
                return Optional.empty();
            }
            public String source() { return "CANONICAL_KPI_RESULT_V1_2"; }
        };
        return new OeeValidationService(access, context, assignments, stopTimes, masses, oeeMetrics,
                new PreviousLocalCalendarDayPolicy(), clock, "GB.KPI.Validator");
    }

    private ActorContext actor() {
        return new ActorContext(T, O, Set.of("GB.KPI.Validator"));
    }

    @Test
    void previousDayIsEligibleWhenSpecificAssignmentExists() {
        var result = service(true).check(actor(), 1, 10, LocalDate.of(2026, 10, 8));
        assertTrue(result.eligible());
    }

    @Test
    void genericPlantAccessDoesNotReplaceKpiSpecificAssignment() {
        assertThrows(AuthorizationException.class, () -> service(false).check(
                actor(), 1, 10, LocalDate.of(2026, 10, 8)));
    }

    @Test
    void dedicatedRoleIsRequiredBeforeOperationalContextIsReturned() {
        assertThrows(AuthorizationException.class, () -> service(true).check(
                new ActorContext(T, O, Set.of("OTHER_ROLE")), 1, 10, LocalDate.of(2026, 10, 8)));
    }

    @Test
    void otherDayIsBlocked() {
        var result = service(true).check(actor(), 1, 10, LocalDate.of(2026, 10, 7));
        assertFalse(result.eligible());
    }

    @Test
    void mondayKeepsSundayAsEligibleDate() {
        Clock monday = Clock.fixed(Instant.parse("2026-10-12T18:00:00Z"), ZoneOffset.UTC);
        var context = service(true, monday).get(actor(), 1, 10);
        assertEquals(LocalDate.of(2026, 10, 12), context.currentOperationDate());
        assertEquals(LocalDate.of(2026, 10, 11), context.eligibleBusinessDate());
        assertFalse(context.skipsWeekends());
        assertFalse(context.skipsHolidays());
    }

    @Test
    void calendarPolicyDoesNotSkipHolidayDates() {
        Clock dayAfterChristmas = Clock.fixed(Instant.parse("2026-12-26T18:00:00Z"), ZoneOffset.UTC);
        var context = service(true, dayAfterChristmas).get(actor(), 1, 10);
        assertEquals(LocalDate.of(2026, 12, 25), context.eligibleBusinessDate());
    }

    @Test
    void temporalContextSeparatesPlantNowFromEditableBusinessDate() {
        var context = service(true).get(actor(), 1, 10);
        assertEquals(ZONE, context.plantTimezone());
        assertEquals(LocalDate.of(2026, 10, 9), context.currentOperationDate());
        assertEquals(LocalDate.of(2026, 10, 8), context.eligibleBusinessDate());
        assertEquals("SINGLE_DATE_ONLY", context.dateSelectionMode());
        assertEquals("PREVIOUS_LOCAL_CALENDAR_DAY", context.calendarRule());
        assertEquals("RESOLVED_AS_PREVIOUS_LOCAL_CALENDAR_DAY", context.elapsed24HourRuleStatus());
        assertEquals("AUTHORITATIVE_CORE_PLANT_TIMEZONE", context.timezoneAuthorityStatus());
        assertTrue(context.issues().stream().noneMatch(i -> "OEE_TEMPORAL_AUTHORITY_PROVISIONAL".equals(i.code())));
    }

    @Test
    void prevalidationRechecksDateAfterPlantCrossesMidnight() {
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-10-10T06:30:00Z"), ZoneOffset.UTC);
        var result = service(true, afterMidnight).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "INC-1", null, null));
        assertFalse(result.businessRulesValid());
        assertTrue(result.issues().stream().anyMatch(issue -> "ONLY_PREVIOUS_DAY_ALLOWED".equals(issue.code())));
    }

    @Test
    void systemReasonRequiresIncident() {
        var result = service(true).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, null, null, null));
        assertFalse(result.businessRulesValid());
    }

    @Test
    void validRulesStillCannotSubmitWithoutPersistence() {
        var result = service(true).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "INC-1", null, null));
        assertTrue(result.businessRulesValid());
        assertFalse(result.readyForSubmission());
    }

    @Test
    void spec01UsesCanonicalKpiResultAsOriginalValue() {
        var result = service(true).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "INC-1", null, null));
        assertTrue(result.businessRulesValid());
        assertEquals(new BigDecimal("86.25"), result.originalValue());
        assertEquals("kpi.kpi_result:77", result.sourceRecordKey());
        assertEquals("CANONICAL_KPI_RESULT_V1_2", result.sourceState());
    }

    @Test
    void spec01ReferenceReturnsCanonicalOriginalValue() {
        var result = service(true).getOeeMetricReference(actor(), 1, 10, LocalDate.of(2026, 10, 8), "OEE");
        assertEquals(new BigDecimal("86.25"), result.originalValue());
        assertEquals("kpi.kpi_result:77", result.sourceRecordKey());
    }

    @Test
    void spec01BlocksWhenCanonicalOriginalValueDoesNotExist() {
        var result = service(true).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "UNKNOWN_OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "INC-1", null, null));
        assertFalse(result.businessRulesValid());
        assertTrue(result.issues().stream().anyMatch(i -> "OEE_ORIGINAL_VALUE_NOT_FOUND".equals(i.code())));
    }

    @Test
    void stopTimeReferenceUsesCanonicalV12AndExplicitProgrammingMapping() {
        var result = service(true).get(actor(), 1, 10, LocalDate.of(2026, 10, 8));
        assertEquals(1, result.entries().size());
        assertEquals(600L, result.entries().getFirst().recordedLineDowntimeSeconds());
        assertEquals("UNPROGRAMMED", result.entries().getFirst().programmingType());
        assertEquals("CONFIGURED_BY_CLASSIFICATION_CODE", result.programmingTypeState());
        assertEquals("stops:line:10:date:2026-10-08:classification:100",
                result.entries().getFirst().sourceAggregateKey());
    }

    @Test
    void stopTimeCorrectionRejectsNegativeValues() {
        var result = service(true).prevalidate(actor(),
                new PrevalidateStopTimeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), 100L,
                        600L, -1L, ValidationReasonType.SYSTEM, "INC-2", null, null));
        assertFalse(result.businessRulesValid());
        assertTrue(result.issues().stream().anyMatch(issue -> "PROPOSED_SECONDS_NEGATIVE".equals(issue.code())));
    }

    @Test
    void stopTimeCorrectionKeepsOriginalAndProposedButCannotSubmitWithoutPersistence() {
        var result = service(true).prevalidate(actor(),
                new PrevalidateStopTimeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), 100L,
                        600L, 540L, ValidationReasonType.SYSTEM, "INC-2", null, null));
        assertTrue(result.businessRulesValid());
        assertFalse(result.readyForSubmission());
        assertEquals(600L, result.originalSeconds());
        assertEquals(600L, result.observedOriginalSeconds());
        assertTrue(result.originalMatchesReference());
        assertEquals(540L, result.proposedSeconds());
        assertEquals("PENDING", result.targetStatusIfPersisted());
        assertEquals("stops:line:10:date:2026-10-08:classification:100", result.sourceAggregateKey());
    }


    @Test
    void stopTimeCorrectionRejectsStaleOrManipulatedOriginalValue() {
        var result = service(true).prevalidate(actor(),
                new PrevalidateStopTimeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), 100L,
                        601L, 540L, ValidationReasonType.SYSTEM, "INC-2", null, null));
        assertFalse(result.businessRulesValid());
        assertEquals(600L, result.observedOriginalSeconds());
        assertFalse(result.originalMatchesReference());
        assertTrue(result.issues().stream().anyMatch(issue -> "ORIGINAL_STOP_TIME_CHANGED".equals(issue.code())));
    }

    @Test
    void stopTimePolicyMakesOpenSpec04RulesExplicit() {
        var policy = service(true).getStopTimePolicy();
        assertEquals("LINE_DATE_STOP_CLASSIFICATION_AGGREGATE", policy.sourceIdentityStrategy());
        assertEquals(List.of("PROGRAMMED", "UNPROGRAMMED"), policy.supportedProgrammingTypes());
        assertEquals("PENDING_FUNCTIONAL_DEFINITION", policy.totalConsistencyPolicy());
        assertEquals("PENDING", policy.targetStatusIfPersisted());
    }

    @Test
    void stopTimeCorrectionRejectsClassificationOutsideCanonicalPlantScope() {
        var result = service(true).prevalidate(actor(),
                new PrevalidateStopTimeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), 999L,
                        600L, 540L, ValidationReasonType.SYSTEM, "INC-2", null, null));
        assertFalse(result.businessRulesValid());
        assertTrue(result.issues().stream().anyMatch(issue -> "STOP_CLASSIFICATION_OUTSIDE_SCOPE".equals(issue.code())));
    }

    @Test
    void massReferenceExposesElaboratedAndDeliveredDecimalsByLineVariableAndUom() {
        var result = service(true).getMassPerformanceReference(actor(), 1, 10, LocalDate.of(2026, 10, 8));
        assertEquals(2, result.entries().size());
        assertEquals(new BigDecimal("1250.5"), result.entries().stream()
                .filter(e -> e.variable() == com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS)
                .findFirst().orElseThrow().originalValue());
        assertEquals(new BigDecimal("1210.25"), result.entries().stream()
                .filter(e -> e.variable() == com.gbconnected.kpivalidation.domain.MassPerformanceVariable.DELIVERED_MASS)
                .findFirst().orElseThrow().originalValue());
    }

    @Test
    void massCorrectionRejectsNegativeProposedValue() {
        var result = service(true).prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS,
                        new BigDecimal("1250.5"), new BigDecimal("-1"), ValidationReasonType.SYSTEM,
                        "INC-5", null, null));
        assertFalse(result.businessRulesValid());
        assertTrue(result.issues().stream().anyMatch(issue -> "PROPOSED_MASS_VALUE_NEGATIVE".equals(issue.code())));
    }

    @Test
    void massCorrectionAcceptsFractionsAndZeroWhenCanonicalOriginalMatches() {
        var result = service(true).prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.DELIVERED_MASS,
                        new BigDecimal("1210.25"), new BigDecimal("1200.125"), ValidationReasonType.OPERATIVE,
                        null, "Operational reconciliation", "Review automatic feed"));
        assertTrue(result.businessRulesValid());
        assertFalse(result.readyForSubmission());
        assertEquals(new BigDecimal("1210.25"), result.observedReferenceValue());
        assertTrue(result.originalMatchesOperationalReference());
        assertEquals("PENDING", result.targetStatusIfPersisted());
        assertEquals("mass:line:10:date:2026-10-08:variable:DELIVERED_MASS:uom:10", result.sourceAggregateKey());
    }

    @Test
    void massCorrectionRejectsStaleOrManipulatedOriginalValue() {
        var result = service(true).prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.DELIVERED_MASS,
                        BigDecimal.ZERO, new BigDecimal("1200.125"), ValidationReasonType.SYSTEM,
                        "INC-5", null, null));
        assertFalse(result.businessRulesValid());
        assertEquals(new BigDecimal("1210.25"), result.observedReferenceValue());
        assertFalse(result.originalMatchesOperationalReference());
        assertTrue(result.issues().stream().anyMatch(issue -> "ORIGINAL_MASS_VALUE_CHANGED".equals(issue.code())));
    }

    @Test
    void massCorrectionBlocksWhenCanonicalOriginalDoesNotExist() {
        MassPerformanceReferencePort emptyMasses = new MassPerformanceReferencePort() {
            public List<MassPerformanceReferenceEntry> findObservedMasses(long plant, long line, LocalDate date, ZoneId zoneId) {
                return List.of();
            }
            public boolean uomExists(long uomId) { return uomId == 10L; }
            public boolean uomIsMassDimension(long uomId) { return uomId == 10L; }
            public String source() { return "empty-test-mass"; }
        };
        AccessScopePort access = (tenant, object, plant) -> plant == 1;
        OperationalContextPort context = new OperationalContextPort() {
            public Optional<PlantContext> findPlant(long plant) { return Optional.of(new PlantContext(1, ZONE, true)); }
            public boolean lineBelongsToPlant(long line, long plant) { return line == 10 && plant == 1; }
        };
        ValidatorAssignmentPort assignments = new ValidatorAssignmentPort() {
            public boolean configured() { return true; }
            public boolean isAssigned(UUID tenant, UUID object, long plant) { return true; }
            public Set<Long> assignedPlants(UUID tenant, UUID object) { return Set.of(1L); }
            public int assignedValidatorCount(long plant) { return 1; }
            public String source() { return "test"; }
        };
        StopTimeReferencePort stopTimes = new StopTimeReferencePort() {
            public List<StopTimeReferenceEntry> findObservedTimes(long plant, long line, LocalDate date, ZoneId zoneId) { return List.of(); }
            public boolean classificationExistsInPlantScope(long plant, long classificationId) { return true; }
            public String source() { return "test"; }
        };
        OeeMetricSourcePort oeeMetrics = new OeeMetricSourcePort() {
            public Optional<OeeMetricSnapshot> findMetric(long plant, long line, LocalDate date, ZoneId zoneId, String metricCode) {
                return Optional.of(new OeeMetricSnapshot(plant, line, date, metricCode, BigDecimal.ONE, "kpi.kpi_result:1"));
            }
            public String source() { return "test-oee"; }
        };
        var svc = new OeeValidationService(access, context, assignments, stopTimes, emptyMasses, oeeMetrics,
                new PreviousLocalCalendarDayPolicy(),
                Clock.fixed(Instant.parse("2026-10-09T18:00:00Z"), ZoneOffset.UTC), "GB.KPI.Validator");
        var result = svc.prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS,
                        BigDecimal.ZERO, BigDecimal.ONE, ValidationReasonType.SYSTEM, "INC-5", null, null));
        assertFalse(result.businessRulesValid());
        assertNull(result.observedReferenceValue());
        assertTrue(result.issues().stream().anyMatch(issue -> "MASS_ORIGINAL_VALUE_NOT_FOUND".equals(issue.code())));
    }

    @Test
    void massPolicyMakesSourceAndOpenRulesExplicit() {
        var policy = service(true).getMassPerformancePolicy();
        assertEquals("LINE_DATE_VARIABLE_UOM_AGGREGATE", policy.sourceIdentityStrategy());
        assertEquals(List.of("ELABORATED_MASS", "DELIVERED_MASS"), policy.supportedVariables());
        assertEquals("PENDING_FUNCTIONAL_DEFINITION", policy.relationPolicy());
        assertEquals("PENDING", policy.targetStatusIfPersisted());
    }

    @Test
    void spec06ReturnsNormalizedSystemJustificationAndDiscardsStaleOperativeFields() {
        var result = service(true).prevalidate(
                actor(),
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "  INC-77  ",
                        "stale cause", "stale plan"));
        assertTrue(result.businessRulesValid());
        assertNotNull(result.justification());
        assertEquals(ValidationReasonType.SYSTEM, result.justification().reasonType());
        assertEquals("INC-77", result.justification().incidentNumber());
        assertNull(result.justification().operationalCause());
        assertNull(result.justification().actionPlan());
        assertTrue(result.issues().stream().anyMatch(issue ->
                "IRRELEVANT_OPERATIVE_DETAILS_DISCARDED".equals(issue.code())));
    }

    @Test
    void spec06ReturnsNormalizedOperativeJustificationAndDiscardsStaleIncident() {
        var result = service(true).prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS,
                        new BigDecimal("1250.5"), new BigDecimal("1251.0"), ValidationReasonType.OPERATIVE,
                        "INC-OLD", "  Reconciliar captura  ", "  Corregir procedimiento  "));
        assertTrue(result.businessRulesValid());
        assertEquals(ValidationReasonType.OPERATIVE, result.justification().reasonType());
        assertNull(result.justification().incidentNumber());
        assertEquals("Reconciliar captura", result.justification().operationalCause());
        assertEquals("Corregir procedimiento", result.justification().actionPlan());
        assertTrue(result.issues().stream().anyMatch(issue ->
                "IRRELEVANT_INCIDENT_NUMBER_DISCARDED".equals(issue.code())));
    }

    @Test
    void spec06ExposesPolicyWithoutInventingLengthOrIncidentFormat() {
        var policy = service(true).getJustificationPolicy();
        assertEquals(List.of("OPERATIVE", "SYSTEM"), policy.allowedReasonTypes());
        assertEquals("PENDING_UX_DEFINITION", policy.textLengthPolicyState());
        assertNull(policy.incidentNumberMaxLength());
        assertNull(policy.operationalCauseMaxLength());
        assertNull(policy.actionPlanMaxLength());
        assertEquals("UNICODE_CODE_POINTS", policy.lengthUnit());
        assertEquals("CAPTURE_ONLY_PATTERN_NOT_DEFINED", policy.incidentFormatValidationState());
        assertNull(policy.incidentNumberRegex());
        assertEquals("PENDING_EXTERNAL_INTEGRATION", policy.gbSupportValidationState());
    }

    @Test
    void exposesOnlyExplicitKpiAssignmentsForCurrentActor() {
        var result = service(true).get(actor());
        assertTrue(result.enabled());
        assertEquals(List.of(1L), result.plantIds());
        assertEquals(2, result.maxValidatorsPerPlant());
    }
    @Test
    void spec07CarriesImmutableActorAndAbsoluteTimestampInPrevalidationEvidence() {
        ActorContext namedActor = new ActorContext(T, O, Set.of("GB.KPI.Validator"),
                "Validator A", "validator.a@example.test");
        var result = service(true).prevalidate(
                namedActor,
                new PrevalidateOeeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), "OEE",
                        new BigDecimal("87.5"), ValidationReasonType.SYSTEM, "INC-7", null, null));

        assertNotNull(result.auditEvidence());
        assertEquals(T, result.auditEvidence().actorTenantId());
        assertEquals(O, result.auditEvidence().actorObjectId());
        assertEquals(T + "/" + O, result.auditEvidence().immutableActorId());
        assertEquals("Validator A", result.auditEvidence().displayNameSnapshot());
        assertEquals("validator.a@example.test", result.auditEvidence().principalNameSnapshot());
        assertEquals(Instant.parse("2026-10-09T18:00:00Z"), result.auditEvidence().eventTimestamp());
        assertEquals("ABSOLUTE_INSTANT_UTC", result.auditEvidence().timestampSemantics());
        assertEquals("PREVALIDATION_ONLY",
                result.auditEvidence().evidencePersistenceState());
        assertEquals("PENDING", result.targetStatusIfPersisted());
        assertFalse(result.readyForSubmission());
    }

    @Test
    void spec01ExposesSemanticWorkflowContractWithoutClaimingPersistence() {
        var policy = service(true).getWorkflowPolicy();
        assertEquals(List.of("PENDING", "APPROVED", "REJECTED", "EXPIRED"), policy.statuses());
        assertEquals("PENDING", policy.initialStatusWhenPersisted());
        assertEquals("KPI_RESULT_ID", policy.sourceIdentityStrategy());
        assertEquals("UNAVAILABLE", policy.persistenceState());
    }

    @Test
    void spec07AuditEvidenceIsPresentForStopTimeAndMassPrevalidationToo() {
        var stop = service(true).prevalidate(actor(),
                new PrevalidateStopTimeCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8), 100L,
                        600L, 540L, ValidationReasonType.SYSTEM, "INC-7", null, null));
        var mass = service(true).prevalidateMassPerformance(actor(),
                new PrevalidateMassPerformanceCorrectionCommand(1, 10, LocalDate.of(2026, 10, 8),
                        10L, com.gbconnected.kpivalidation.domain.MassPerformanceVariable.ELABORATED_MASS,
                        new BigDecimal("1250.5"), new BigDecimal("1251.0"), ValidationReasonType.SYSTEM,
                        "INC-7", null, null));

        assertEquals(Instant.parse("2026-10-09T18:00:00Z"), stop.auditEvidence().eventTimestamp());
        assertEquals(Instant.parse("2026-10-09T18:00:00Z"), mass.auditEvidence().eventTimestamp());
    }

    @Test
    void spec07ExposesPolicyWithoutInventingPresentationTimezoneOrVisibleUserField() {
        var policy = service(true).getAuditPolicy();
        assertEquals("ENTRA_TENANT_ID_PLUS_OBJECT_ID", policy.immutableUserIdentifier());
        assertEquals("NOT_AVAILABLE_IN_CURRENT_ADAPTER", policy.canonicalUserLinkState());
        assertEquals("ENTRA_CLAIMS_ONLY", policy.canonicalUserIdentitySource());
        assertEquals("ABSOLUTE_INSTANT_UTC", policy.timestampSemantics());
        assertEquals("PENDING_PRESENTATION_TIMEZONE_DEFINITION", policy.displayTimezoneState());
        assertEquals("UNAVAILABLE", policy.persistenceState());
    }

}
