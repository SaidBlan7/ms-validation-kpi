package com.gbconnected.kpivalidation.application.manual;

public record ManualKpiPolicyView(
        int historicalWindowHours,
        int editWindowHours,
        int oeePeriodHours,
        String dateBasis,
        boolean historicalBoundaryInclusive,
        boolean weekendsAllowed,
        boolean holidaysAllowed,
        boolean calendarDayFilteringApplied,
        String dstBehavior,
        String editWindowBasis,
        boolean editMayContinueAfterDateLeavesHistoricalWindow,
        String automaticPrecedence,
        String deactivationBehavior,
        String featureConfigurationCode,
        String authorizationCapabilityCode,
        boolean persistenceAvailable,
        String persistenceSource) {}
