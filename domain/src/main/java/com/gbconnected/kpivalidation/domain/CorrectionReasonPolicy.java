package com.gbconnected.kpivalidation.domain;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Closed technical policy for Spec 06 text fields.
 * The service intentionally keeps incident validation capture-only until GB Support
 * exposes an approved contract. Lengths are fixed here so all runtimes enforce the
 * same behavior.
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
                throw new IllegalArgumentException("incidentNumberRegex is invalid", ex);
            }
        }
    }

    public static CorrectionReasonPolicy closedDefaults() {
        return new CorrectionReasonPolicy(200, 2000, 2000, null);
    }

    /** Kept for compatibility with older tests/callers; closed flows should use closedDefaults(). */
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
