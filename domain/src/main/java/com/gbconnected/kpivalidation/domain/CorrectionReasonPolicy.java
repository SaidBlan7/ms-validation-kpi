package com.gbconnected.kpivalidation.domain;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configurable technical policy for Spec 06 text fields.
 * A max length of 0 means the business/UX maximum is still undefined and is not enforced.
 * An empty incident regex means incident format remains capture-only.
 */
public record CorrectionReasonPolicy(
        int incidentNumberMaxLength,
        int operationalCauseMaxLength,
        int actionPlanMaxLength,
        String incidentNumberRegex) {

    public CorrectionReasonPolicy {
        if (incidentNumberMaxLength < 0 || operationalCauseMaxLength < 0 || actionPlanMaxLength < 0) {
            throw new IllegalArgumentException("Justification maximum lengths cannot be negative");
        }
        incidentNumberRegex = normalize(incidentNumberRegex);
        if (incidentNumberRegex != null) {
            try {
                Pattern.compile(incidentNumberRegex);
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("GBC_INCIDENT_NUMBER_REGEX is invalid", ex);
            }
        }
    }

    public static CorrectionReasonPolicy unbounded() {
        return new CorrectionReasonPolicy(0, 0, 0, null);
    }

    public boolean incidentLengthConfigured() { return incidentNumberMaxLength > 0; }
    public boolean operationalCauseLengthConfigured() { return operationalCauseMaxLength > 0; }
    public boolean actionPlanLengthConfigured() { return actionPlanMaxLength > 0; }
    public boolean incidentRegexConfigured() { return incidentNumberRegex != null; }

    public Integer incidentNumberMaxLengthOrNull() { return incidentLengthConfigured() ? incidentNumberMaxLength : null; }
    public Integer operationalCauseMaxLengthOrNull() { return operationalCauseLengthConfigured() ? operationalCauseMaxLength : null; }
    public Integer actionPlanMaxLengthOrNull() { return actionPlanLengthConfigured() ? actionPlanMaxLength : null; }

    void validateIncidentFormat(String incidentNumber) {
        if (incidentRegexConfigured() && incidentNumber != null && !Pattern.matches(incidentNumberRegex, incidentNumber)) {
            throw new DomainValidationException("incidentNumber does not match the configured incident format");
        }
    }

    static void validateLength(String field, String value, int maxLength) {
        if (value == null || maxLength <= 0) return;
        int length = value.codePointCount(0, value.length());
        if (length > maxLength) {
            throw new DomainValidationException(field + " exceeds the configured maximum length of " + maxLength + " characters");
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
