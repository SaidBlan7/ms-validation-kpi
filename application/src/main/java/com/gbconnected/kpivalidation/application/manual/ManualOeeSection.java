package com.gbconnected.kpivalidation.application.manual;
import java.util.List;
public record ManualOeeSection(List<ManualStopTimeInput> stopTimes, List<ManualPerformanceInput> performance) {
    public ManualOeeSection { stopTimes = stopTimes == null ? List.of() : List.copyOf(stopTimes); performance = performance == null ? List.of() : List.copyOf(performance); }
    public boolean empty() { return stopTimes.isEmpty() && performance.isEmpty(); }
}
