package com.gerald.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;

public record ChemicalState(
    String chemicalId,
    double mass,
    double density,
    double temperature,
    double charge,
    double energy
) {
    public static ChemicalState empty() {
        return new ChemicalState("minecraft:air", 0.0, 0.0, 293.0, 0.0, 0.0);
    }

    public static ChemicalState load(CompoundTag tag) {
        String chemicalId = tag.getString("chemical_id");
        return new ChemicalState(
            chemicalId.isBlank() ? "minecraft:air" : chemicalId,
            tag.getDouble("mass"),
            tag.getDouble("density"),
            tag.getDouble("temperature"),
            tag.getDouble("charge"),
            tag.getDouble("energy")
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("chemical_id", chemicalId);
        tag.putDouble("mass", mass);
        tag.putDouble("density", density);
        tag.putDouble("temperature", temperature);
        tag.putDouble("charge", charge);
        tag.putDouble("energy", energy);
        return tag;
    }

    public ChemicalState merge(ChemicalState other) {
        if (other.mass <= 0.0) return this;
        if (mass <= 0.0) return other;
        double combinedMass = mass + other.mass;
        return new ChemicalState(
            chemicalId,
            combinedMass,
            density + other.density,
            ((temperature * mass) + (other.temperature * other.mass)) / combinedMass,
            ((charge * mass) + (other.charge * other.mass)) / combinedMass,
            energy + other.energy
        );
    }

    public ChemicalState withMass(double nextMass) {
        double bounded = Math.max(0.0, nextMass);
        double ratio = mass <= 0.0 ? 0.0 : bounded / mass;
        return new ChemicalState(chemicalId, bounded, density * ratio, temperature, charge, energy * ratio);
    }

    public ChemicalState withEnergy(double nextEnergy) {
        return new ChemicalState(chemicalId, mass, density, temperature, charge, Math.max(0.0, nextEnergy));
    }
}
