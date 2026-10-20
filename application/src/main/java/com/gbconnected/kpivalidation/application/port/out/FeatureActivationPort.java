package com.gbconnected.kpivalidation.application.port.out;
public interface FeatureActivationPort {
    boolean configured();
    boolean enabledForPlant(long plantId);
    String source();
    String configurationCode();

    static FeatureActivationPort enabledByDefault(String code) {
        return new FeatureActivationPort() {
            @Override public boolean configured() { return true; }
            @Override public boolean enabledForPlant(long plantId) { return plantId > 0; }
            @Override public String source() { return "MEMORY_FEATURE_ACTIVATION"; }
            @Override public String configurationCode() { return code; }
        };
    }
}
