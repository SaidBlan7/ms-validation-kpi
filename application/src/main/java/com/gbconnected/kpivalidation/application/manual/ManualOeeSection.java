package com.gbconnected.kpivalidation.application.manual;

import java.util.List;

public record ManualOeeSection(List<ManualStopTimeInput> stopTimes, ManualMassPerformanceInput massPerformance) {
    public ManualOeeSection {
        stopTimes = stopTimes == null ? List.of() : List.copyOf(stopTimes);
    }
    public boolean empty() { return stopTimes.isEmpty() && (massPerformance == null || massPerformance.empty()); }
}
