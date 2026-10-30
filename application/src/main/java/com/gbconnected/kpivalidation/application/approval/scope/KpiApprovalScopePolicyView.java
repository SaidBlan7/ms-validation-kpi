package com.gbconnected.kpivalidation.application.approval.scope;
public record KpiApprovalScopePolicyView(String approverCardinality, String assignmentScope,
        String revocationEffect, String pendingAfterReassignment, String globalApprovalFlow,
        boolean administrationAvailable, String administrationCapabilityCode) { }
