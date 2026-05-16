package com.gerald.latentchemlib.data;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.Element;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LatentDataManager implements PreparableReloadListener {
    public static final LatentDataManager INSTANCE = new LatentDataManager();
    private static final Gson GSON = new Gson();

    private volatile Map<String, ChemicalTraits> traits = Map.of();
    private volatile SchedulerProfile schedulerProfile = SchedulerProfile.defaults();

    public ChemicalTraits traits(String chemicalId) {
        ChemicalTraits configured = traits.get(chemicalId);
        return configured == null ? deriveFromRegistry(chemicalId) : configured;
    }

    public SchedulerProfile schedulerProfile() {
        return schedulerProfile;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager, ProfilerFiller prepProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resourceManager), backgroundExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(snapshot -> {
                traits = snapshot.traits();
                schedulerProfile = snapshot.schedulerProfile();
                LatentChemlibMod.LOGGER.info("Loaded {} latent chemical trait overrides", traits.size());
            }, gameExecutor);
    }

    private Snapshot load(ResourceManager resourceManager) {
        Map<String, ChemicalTraits> loadedTraits = new HashMap<>();
        resourceManager.listResources("chemical_traits", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                String chemicalId = text(json, "chemical", id.getNamespace() + ":" + id.getPath());
                loadedTraits.put(chemicalId, traitsFromJson(json, deriveFromRegistry(chemicalId)));
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent chemical trait file {}", id, ex);
            }
        });

        SchedulerProfile profile = SchedulerProfile.defaults();
        for (var entry : resourceManager.listResources("scheduler_profiles", id -> id.getPath().endsWith("default.json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                profile = new SchedulerProfile(
                    integer(json, "cloud_updates_per_second", profile.cloudUpdatesPerSecond()),
                    integer(json, "neighbor_ops_per_second", profile.neighborOpsPerSecond()),
                    integer(json, "escape_scans_per_second", profile.escapeScansPerSecond()),
                    integer(json, "nuclear_inventory_scans_per_second", profile.nuclearInventoryScansPerSecond()),
                    integer(json, "stack_mutations_per_second", profile.stackMutationsPerSecond()),
                    integer(json, "heat_radiation_emissions_per_second", profile.heatRadiationEmissionsPerSecond())
                );
            } catch (Exception ex) {
                LatentChemlibMod.LOGGER.warn("Ignoring invalid latent scheduler profile {}", entry.getKey(), ex);
            }
        }
        return new Snapshot(Map.copyOf(loadedTraits), profile);
    }

    private ChemicalTraits traitsFromJson(JsonObject json, ChemicalTraits fallback) {
        return new ChemicalTraits(
            number(json, "volatility", fallback.volatility()),
            number(json, "cohesion", fallback.cohesion()),
            number(json, "heat_capacity", fallback.heatCapacity()),
            number(json, "conductivity", fallback.conductivity()),
            number(json, "ionization_tendency", fallback.ionizationTendency()),
            number(json, "neutron_instability", fallback.neutronInstability()),
            number(json, "neutron_absorption", fallback.neutronAbsorption()),
            number(json, "scattering", fallback.scattering()),
            number(json, "containment_strength", fallback.containmentStrength()),
            curve(json == null ? null : json.getAsJsonObject("fusion_barrier"), fallback.fusionBarrier())
        );
    }

    public ChemicalTraits deriveFromRegistry(String chemicalId) {
        ChemicalTraits fallback = ChemicalTraits.fallback();
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null || !(ForgeRegistries.ITEMS.getValue(id) instanceof Chemical chemical)) {
            return fallback;
        }
        Object item = ForgeRegistries.ITEMS.getValue(id);
        double mass = item instanceof Element element ? Math.max(1.0, element.getAtomicNumber()) : Math.max(1.0, chemical.getAbbreviation().length() * 4.0);
        double volatilitySeed = chemical.getMatterState() == MatterState.GAS ? 2.4 : chemical.getMatterState() == MatterState.LIQUID ? 0.65 : 0.12;
        double instability = item instanceof Element element ? Math.max(0.0, Math.pow(Math.max(0, element.getAtomicNumber() - 82), 2.0) / 144.0) : 0.0;
        return new ChemicalTraits(
            volatilitySeed / Math.sqrt(mass),
            Math.sqrt(mass) * 0.18,
            0.8 + mass * 0.015,
            0.06 + chemical.getEffects().size() * 0.02,
            0.04 + 1.0 / Math.sqrt(mass),
            instability,
            0.04 + mass * 0.002,
            0.03 + Math.log1p(mass) * 0.01,
            0.0,
            new NumericCurve(PresetCurve.EXPONENTIAL, 1_400.0, 55.0, 1.35, 0.0, 120_000.0)
        );
    }

    private static NumericCurve curve(JsonObject json, NumericCurve fallback) {
        if (json == null) return fallback;
        PresetCurve type = PresetCurve.valueOf(text(json, "type", fallback.type().name()).toUpperCase());
        return new NumericCurve(
            type,
            number(json, "offset", fallback.offset()),
            number(json, "scale", fallback.scale()),
            number(json, "exponent", fallback.exponent()),
            number(json, "midpoint", fallback.midpoint()),
            number(json, "max", fallback.max())
        );
    }

    private static String text(JsonObject json, String key, String fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsString();
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsDouble();
    }

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement value = json == null ? null : json.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private record Snapshot(Map<String, ChemicalTraits> traits, SchedulerProfile schedulerProfile) {}
}
