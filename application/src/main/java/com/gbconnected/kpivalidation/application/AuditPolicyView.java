package com.gbconnected.kpivalidation.application;

import java.util.List;

public record AuditPolicyView(
        String immutableUserIdentifier,
        List<String> optionalPresentationSnapshots,
        String timestampSemantics,
        String displayTimezoneState,
        String persistenceState,
        String disabledAccountEvidenceState,
        String downstreamReconciliationState) {
}
