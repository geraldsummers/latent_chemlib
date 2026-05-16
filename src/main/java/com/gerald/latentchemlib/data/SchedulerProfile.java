package com.gerald.latentchemlib.data;

public record SchedulerProfile(
    int cloudUpdatesPerSecond,
    int neighborOpsPerSecond,
    int escapeScansPerSecond,
    int nuclearInventoryScansPerSecond,
    int stackMutationsPerSecond,
    int heatRadiationEmissionsPerSecond
) {
    public static SchedulerProfile defaults() {
        return new SchedulerProfile(256, 768, 64, 96, 32, 64);
    }
}
