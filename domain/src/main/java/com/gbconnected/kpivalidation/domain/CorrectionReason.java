package com.gbconnected.kpivalidation.domain;

public record CorrectionReason(
        ValidationReasonType type,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {

    public void validate() {
        normalized();
    }

    /**
     * Validates Spec 06 reason semantics and returns the canonical payload that may
     * travel with a future validation proposal. Irrelevant fields are intentionally
     * cleared so changing the selected reason cannot carry stale evidence from the
     * previous reason type.
     */
    public CorrectionReason normalized() {
        return normalized(CorrectionReasonPolicy.closedDefaults());
    }

    public CorrectionReason normalized(CorrectionReasonPolicy policy) {
        if (type == null) throw new DomainValidationException("reasonType is required");
        if (policy == null) policy = CorrectionReasonPolicy.unbounded();

        String incident = normalizeSingleLine("incidentNumber", incidentNumber);
        String cause = normalizeFreeText("operationalCause", operationalCause);
        String plan = normalizeFreeText("actionPlan", actionPlan);

        if (type == ValidationReasonType.SYSTEM) {
            if (blank(incident)) {
                throw new DomainValidationException("incidentNumber is required for SYSTEM reason");
            }
            CorrectionReasonPolicy.validateLength("incidentNumber", incident, policy.incidentNumberMaxLength());
            policy.validateIncidentFormat(incident);
            return new CorrectionReason(type, incident, null, null);
        }
        if (blank(cause)) {
            throw new DomainValidationException("operationalCause is required for OPERATIVE reason");
        }
        if (blank(plan)) {
            throw new DomainValidationException("actionPlan is required for OPERATIVE reason");
        }
        CorrectionReasonPolicy.validateLength("operationalCause", cause, policy.operationalCauseMaxLength());
        CorrectionReasonPolicy.validateLength("actionPlan", plan, policy.actionPlanMaxLength());
        return new CorrectionReason(type, null, cause, plan);
    }

    private static String normalizeSingleLine(String field, String value) {
        if (value == null) return null;
        String normalized = value.strip();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new DomainValidationException(field + " contains unsupported control characters");
            }
        }
        return normalized;
    }

    private static String normalizeFreeText(String field, String value) {
        if (value == null) return null;
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isISOControl(ch) && ch != '\n' && ch != '\t') {
                throw new DomainValidationException(field + " contains unsupported control characters");
            }
        }
        return normalized;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
