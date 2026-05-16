package com.gerald.latentchemlib.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericCurveTest {
    @Test
    void presetCurvesReturnFiniteValues() {
        for (PresetCurve type : PresetCurve.values()) {
            NumericCurve curve = new NumericCurve(type, 1.0, 2.0, 1.3, 4.0, 100.0);
            assertTrue(Double.isFinite(curve.sample(8.0)), type.name());
        }
    }
}
