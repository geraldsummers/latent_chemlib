package com.gerald.latentchemlib.data;

import com.gerald.latentchemlib.sim.ChemicalState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionRuleTest {
    @Test
    void ruleMatchesAllStateAndHeatThresholds() {
        ReactionRule rule = new ReactionRule(
            "test",
            "chemlib:hydrogen",
            "chemlib:helium",
            "",
            100.0,
            1000.0,
            1.0,
            2000.0,
            0.5,
            100.0,
            -0.25,
            -500.0,
            40.0f,
            120.0f
        );
        ChemicalState state = new ChemicalState("chemlib:hydrogen", 120.0, 2.0, 1200.0, 1.2, 2500.0);
        assertTrue(rule.matches(state, 40.0f));
        assertFalse(rule.matches(state, 39.0f));
        assertFalse(rule.matches(state.withMass(90.0), 40.0f));
    }

    @Test
    void applyTransformsChemicalAndKeepsBoundedFields() {
        ReactionRule rule = new ReactionRule(
            "test",
            "chemlib:hydrogen",
            "chemlib:helium",
            "",
            1.0,
            293.0,
            0.0,
            0.0,
            0.5,
            100.0,
            -2.0,
            -500.0,
            0.0f,
            0.0f
        );
        ChemicalState out = rule.apply(new ChemicalState("chemlib:hydrogen", 200.0, 4.0, 500.0, 1.0, 250.0));
        assertEquals("chemlib:helium", out.chemicalId());
        assertEquals(100.0, out.mass());
        assertEquals(4.0, out.density());
        assertEquals(600.0, out.temperature());
        assertEquals(0.0, out.charge());
        assertEquals(0.0, out.energy());
    }
}
