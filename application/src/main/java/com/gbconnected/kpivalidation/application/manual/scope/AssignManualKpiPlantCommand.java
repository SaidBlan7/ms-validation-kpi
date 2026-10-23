package com.gbconnected.kpivalidation.application.manual.scope;
public record AssignManualKpiPlantCommand(long targetUserId, long plantId, String reason) { }
