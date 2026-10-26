package com.gbconnected.kpivalidation.appservice.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ManualKpiHttpRequest(long plantId,long lineId,LocalDate businessDate,Long expectedVersion,Oee oee,Waste waste) {
    public record Oee(List<StopTime> stopTimes,MassPerformance massPerformance) {}
    public record StopTime(long stopClassificationId,BigDecimal hours) {}
    public record MassPerformance(BigDecimal elaboratedMass,BigDecimal deliveredMass) {}
    public record Waste(List<WasteEntry> entries,BigDecimal producedTonnes) {}
    public record WasteEntry(long wasteClassificationId,Long wasteSubclassificationId,BigDecimal kilograms) {}
}
