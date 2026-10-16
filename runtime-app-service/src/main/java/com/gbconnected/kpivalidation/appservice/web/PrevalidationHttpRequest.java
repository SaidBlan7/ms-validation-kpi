package com.gbconnected.kpivalidation.appservice.web;
import com.gbconnected.kpivalidation.domain.ValidationReasonType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PrevalidationHttpRequest(@Positive long plantId,@Positive long lineId,@NotNull LocalDate businessDate,@NotBlank String metricCode,@NotNull BigDecimal proposedValue,@NotNull ValidationReasonType reasonType,String incidentNumber,String operationalCause,String actionPlan) {}
