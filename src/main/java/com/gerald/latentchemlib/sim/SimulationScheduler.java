package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.data.SchedulerProfile;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumMap;
import java.util.Map;

public class SimulationScheduler {
    public static final SimulationScheduler INSTANCE = new SimulationScheduler();

    public enum Budget {
        CLOUD_UPDATES,
        NEIGHBOR_OPS,
        ESCAPE_SCANS,
        NUCLEAR_INVENTORY_SCANS,
        STACK_MUTATIONS,
        HEAT_RADIATION_EMISSIONS
    }

    private final Map<ResourceKey<Level>, EnumMap<Budget, Integer>> spentByLevel = new Object2ObjectOpenHashMap<>();

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide() && event.level.getGameTime() % 20L == 0L) {
            spentByLevel.remove(event.level.dimension());
        }
    }

    public boolean trySpend(Level level, Budget budget, int amount) {
        SchedulerProfile profile = LatentDataManager.INSTANCE.schedulerProfile();
        int max = switch (budget) {
            case CLOUD_UPDATES -> profile.cloudUpdatesPerSecond();
            case NEIGHBOR_OPS -> profile.neighborOpsPerSecond();
            case ESCAPE_SCANS -> profile.escapeScansPerSecond();
            case NUCLEAR_INVENTORY_SCANS -> profile.nuclearInventoryScansPerSecond();
            case STACK_MUTATIONS -> profile.stackMutationsPerSecond();
            case HEAT_RADIATION_EMISSIONS -> profile.heatRadiationEmissionsPerSecond();
        };
        EnumMap<Budget, Integer> spent = spentByLevel.computeIfAbsent(level.dimension(), ignored -> new EnumMap<>(Budget.class));
        int current = spent.getOrDefault(budget, 0);
        if (current + amount > max) return false;
        spent.put(budget, current + amount);
        return true;
    }
}
