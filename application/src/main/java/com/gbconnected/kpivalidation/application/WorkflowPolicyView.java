package com.gbconnected.kpivalidation.application;

import java.util.List;

public record WorkflowPolicyView(
        List<String> statuses,
        String initialStatusWhenPersisted,
        String sourceIdentityStrategy,
        String persistenceState,
        String singleModificationState,
        String concurrencyState,
        String approvalApplicationState) {
}
