package com.gbconnected.kpivalidation.application.manual.scope;
public record RevokeManualKpiPlantCommand(long targetUserId, long plantId, String reason) { }
