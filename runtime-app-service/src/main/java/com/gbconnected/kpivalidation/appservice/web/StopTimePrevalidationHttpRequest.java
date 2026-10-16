package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record StopTimePrevalidationHttpRequest(
        @Positive long plantId,
        @Positive long lineId,
        @NotNull LocalDate businessDate,
        @Positive long stopClassificationId,
        @NotNull @PositiveOrZero Long originalSeconds,
        @NotNull @PositiveOrZero Long proposedSeconds,
        @NotNull ValidationReasonType reasonType,
        String incidentNumber,
        String operationalCause,
        String actionPlan) {
}
