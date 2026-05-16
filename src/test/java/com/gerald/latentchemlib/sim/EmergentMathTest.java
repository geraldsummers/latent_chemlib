package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.NumericCurve;
import com.gerald.latentchemlib.data.PresetCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergentMathTest {
    private final ChemicalTraits traits = new ChemicalTraits(
        1.2, 0.3, 1.0, 0.2, 0.4, 1.5, 0.2, 0.1, 0.0,
        new NumericCurve(PresetCurve.EXPONENTIAL, 1_000.0, 80.0, 1.3, 0.0, 200_000.0)
    );

    @Test
    void escapeEmergesFromPressureAndContainment() {
        ChemicalState volatileState = new ChemicalState("chemlib:hydrogen", 250.0, 2.0, 500.0, 0.0, 80.0);
        assertTrue(EmergentMath.escapePressure(volatileState, traits, 0.0) > 0.0);
        assertTrue(EmergentMath.escapePressure(volatileState, traits, 10_000.0) < 0.0);
    }

    @Test
    void diffusionUsesGradientAndCohesion() {
        ChemicalState source = new ChemicalState("chemlib:hydrogen", 500.0, 4.0, 600.0, 0.0, 120.0);
        assertTrue(EmergentMath.pressureGradient(source, 0.1, traits) > EmergentMath.pressureGradient(source, 3.9, traits));
        assertTrue(EmergentMath.diffusionMass(source, traits) > 0.0);
    }

    @Test
    void fusionInterceptUsesContinuousEnergyTerms() {
        ChemicalState cool = new ChemicalState("chemlib:hydrogen", 500.0, 0.4, 300.0, 0.0, 100.0);
        ChemicalState hot = new ChemicalState("chemlib:hydrogen", 500.0, 8.0, 8_000.0, 2.0, 80_000.0);
        assertFalse(EmergentMath.fusionIntercept(cool, cool, traits, 2, 0.5));
        assertTrue(EmergentMath.fusionIntercept(hot, hot, traits, 2, 2.0));
    }

    @Test
    void neutronFluxIsContinuousAndModerated() {
        ChemicalState state = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        double unmoderated = EmergentMath.neutronFlux(state, traits, 0.0);
        double moderated = EmergentMath.neutronFlux(state, traits, 10.0);
        assertTrue(unmoderated > moderated);
        assertTrue(moderated > 0.0);
    }
}
