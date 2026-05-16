package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.SchedulerProfile;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class SimulationBudgetLedger<K> {
    private final Map<K, EnumMap<SimulationBudget, Integer>> spentByKey = new HashMap<>();

    public boolean trySpend(K key, SimulationBudget budget, int amount, SchedulerProfile profile) {
        if (amount <= 0) return true;
        int max = limit(budget, profile);
        EnumMap<SimulationBudget, Integer> spent = spentByKey.computeIfAbsent(key, ignored -> new EnumMap<>(SimulationBudget.class));
        int current = spent.getOrDefault(budget, 0);
        if (current + amount > max) return false;
        spent.put(budget, current + amount);
        return true;
    }

    public int spent(K key, SimulationBudget budget) {
        EnumMap<SimulationBudget, Integer> spent = spentByKey.get(key);
        return spent == null ? 0 : spent.getOrDefault(budget, 0);
    }

    public void reset(K key) {
        spentByKey.remove(key);
    }

    public void resetAll() {
        spentByKey.clear();
    }

    public static int limit(SimulationBudget budget, SchedulerProfile profile) {
        return switch (budget) {
            case CLOUD_UPDATES -> profile.cloudUpdatesPerSecond();
            case NEIGHBOR_OPS -> profile.neighborOpsPerSecond();
            case ESCAPE_SCANS -> profile.escapeScansPerSecond();
            case NUCLEAR_INVENTORY_SCANS -> profile.nuclearInventoryScansPerSecond();
            case STACK_MUTATIONS -> profile.stackMutationsPerSecond();
            case HEAT_RADIATION_EMISSIONS -> profile.heatRadiationEmissionsPerSecond();
        };
    }
}
