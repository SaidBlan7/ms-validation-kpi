package com.gbconnected.kpivalidation.application.approval;
public record ApprovalPolicyView(String decisionDeadlineSource,String concurrencyStrategy,String decisionMutability,
        boolean commentRequired,String oeeSupport,String wasteSupport,String effectiveValueRule) {}
