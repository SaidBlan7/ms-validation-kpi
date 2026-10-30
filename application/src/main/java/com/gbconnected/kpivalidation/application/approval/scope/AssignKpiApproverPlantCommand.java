package com.gbconnected.kpivalidation.application.approval.scope;
public record AssignKpiApproverPlantCommand(long targetUserId, long plantId, String reason) { }
