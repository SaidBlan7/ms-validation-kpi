package com.gbconnected.kpivalidation.application.manual;
import java.time.LocalDate;
public record SaveManualKpiCommand(long plantId, long lineId, LocalDate businessDate, ManualOeeSection oee, ManualWasteSection waste) {}
