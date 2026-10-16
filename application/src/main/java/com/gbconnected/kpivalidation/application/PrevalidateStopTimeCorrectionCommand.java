package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import java.time.LocalDate;

public record PrevalidateStopTimeCorrectionCommand(
        long plantId,
        long lineId,
        LocalDate businessDate,
        long stopClassificationId,
        Long originalSeconds,
        Long proposedSeconds,
        ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {
}
