package com.gbconnected.kpivalidation.application.manual;
public record ManualKpiPolicyView(int historicalWindowHours, int editWindowHours, String dateBasis, String editWindowBasis,
        String automaticPrecedence, String deactivationBehavior, String featureConfigurationCode, String authorizationCapabilityCode,
        boolean persistenceAvailable, String persistenceSource) {}
