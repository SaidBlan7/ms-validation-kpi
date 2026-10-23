package com.gbconnected.kpivalidation.application.port.out;
public interface FeatureActivationPort {
    boolean configured();
    boolean enabledForPlant(long plantId);
    default boolean enabledForScope(long plantId, long lineId) { return enabledForPlant(plantId); }
    String source();
    String configurationCode();

    static FeatureActivationPort enabledByDefault(String code) {
        return new FeatureActivationPort() {
            @Override public boolean configured() { return true; }
            @Override public boolean enabledForPlant(long plantId) { return plantId > 0; }
            @Override public boolean enabledForScope(long plantId, long lineId) { return plantId > 0 && lineId > 0; }
            @Override public String source() { return "MEMORY_FEATURE_ACTIVATION"; }
            @Override public String configurationCode() { return code; }
        };
    }
}
