package com.gerald.latentchemlib.sim;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChemicalStateTest {
    @Test
    void emptyStateIsSafeAmbientAir() {
        ChemicalState state = ChemicalState.empty();
        assertEquals("minecraft:air", state.chemicalId());
        assertEquals(0.0, state.mass());
        assertEquals(0.0, state.density());
        assertEquals(293.0, state.temperature());
        assertEquals(0.0, state.charge());
        assertEquals(0.0, state.energy());
    }

    @Test
    void saveLoadRoundTripPreservesFields() {
        ChemicalState original = new ChemicalState("chemlib:hydrogen", 125.0, 2.5, 600.0, 0.75, 9_000.0);
        ChemicalState loaded = ChemicalState.load(original.save());
        assertEquals(original, loaded);
    }

    @Test
    void loadFallsBackToAirWhenIdIsBlank() {
        CompoundTag tag = new CompoundTag();
        tag.putString("chemical_id", "");
        tag.putDouble("temperature", 123.0);
        ChemicalState loaded = ChemicalState.load(tag);
        assertEquals("minecraft:air", loaded.chemicalId());
        assertEquals(123.0, loaded.temperature());
    }

    @Test
    void mergeHandlesEmptySidesAndWeightedFields() {
        ChemicalState hydrogen = new ChemicalState("chemlib:hydrogen", 100.0, 4.0, 400.0, 0.2, 50.0);
        ChemicalState helium = new ChemicalState("chemlib:helium", 300.0, 1.0, 800.0, 0.6, 90.0);

        assertSame(hydrogen, hydrogen.merge(ChemicalState.empty()));
        assertSame(hydrogen, ChemicalState.empty().merge(hydrogen));

        ChemicalState merged = hydrogen.merge(helium);
        assertEquals("chemlib:hydrogen", merged.chemicalId());
        assertEquals(400.0, merged.mass());
        assertEquals(4.0, merged.density());
        assertEquals(700.0, merged.temperature());
        assertEquals(0.5, merged.charge());
        assertEquals(140.0, merged.energy());
    }

    @Test
    void withMassScalesEnergyAndClampsEmptyMatter() {
        ChemicalState state = new ChemicalState("chemlib:radon", 100.0, 4.0, 700.0, 1.0, 500.0);
        ChemicalState half = state.withMass(50.0);
        assertEquals(50.0, half.mass());
        assertEquals(4.0, half.density());
        assertEquals(250.0, half.energy());

        ChemicalState empty = state.withMass(-1.0);
        assertEquals(0.0, empty.mass());
        assertEquals(0.0, empty.density());
        assertEquals(0.0, empty.energy());

        ChemicalState fromEmpty = ChemicalState.empty().withMass(10.0);
        assertEquals(10.0, fromEmpty.mass());
        assertEquals(0.0, fromEmpty.energy());
    }

    @Test
    void withEnergyClampsNegativeEnergy() {
        ChemicalState state = new ChemicalState("chemlib:argon", 10.0, 1.0, 300.0, 0.0, 20.0);
        assertEquals(0.0, state.withEnergy(-200.0).energy());
        assertEquals(75.0, state.withEnergy(75.0).energy());
    }

    @Test
    void cloudDiffusionTierBecomesMoreTransparentAsDensityFalls() {
        assertEquals(0, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 3.0, 293.0, 0.0, 0.0)));
        assertEquals(1, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 2.0, 293.0, 0.0, 0.0)));
        assertEquals(2, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 0.5, 293.0, 0.0, 0.0)));
        assertEquals(3, ChemicalCloudVisuals.diffusionTier(new ChemicalState("chemlib:chlorine", 100.0, 0.1, 293.0, 0.0, 0.0)));
    }
}
