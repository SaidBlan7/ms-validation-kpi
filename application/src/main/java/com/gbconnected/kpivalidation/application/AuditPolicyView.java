package com.gbconnected.kpivalidation.application;

import java.util.List;

public record AuditPolicyView(
        String immutableUserIdentifier,
        String canonicalUserLinkState,
        String canonicalUserIdentitySource,
        List<String> optionalPresentationSnapshots,
        String presentationSnapshotPrecedence,
        String timestampSemantics,
        String displayTimezoneState,
        String persistenceState,
        String disabledAccountEvidenceState,
        String retentionPolicyState,
        String downstreamReconciliationState) {
}
