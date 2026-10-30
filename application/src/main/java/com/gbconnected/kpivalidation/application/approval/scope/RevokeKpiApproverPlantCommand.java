package com.gbconnected.kpivalidation.application.approval.scope;
public record RevokeKpiApproverPlantCommand(long targetUserId, long plantId, String reason) { }
