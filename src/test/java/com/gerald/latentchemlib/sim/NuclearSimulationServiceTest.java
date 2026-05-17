package com.gerald.latentchemlib.sim;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearSimulationServiceTest {
    @Test
    void longHalfLifeUraniumHasNoNormalWindowEventWithoutFlux() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            2.0,
            NuclearSimulationService.NuclearEnvironment.EMPTY,
            RandomSource.create(42L)
        );

        assertTrue(event.isEmpty());
    }

    @Test
    void highFluxUraniumCanInduceOneFissionEvent() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.0, 0.0, 40_000.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            1.0,
            flux,
            RandomSource.create(42L)
        );

        assertTrue(event.isPresent());
        assertEquals(NuclearSimulationService.NuclearEventType.FISSION, event.get().type());
        assertEquals("chemlib:barium", event.get().outputState().chemicalId());
    }

    @Test
    void scannerCursorResumesFromAdvancedPosition() {
        assertEquals(0, NuclearSurfaceScanner.advanceCursor(0, 0, 4));
        assertEquals(3, NuclearSurfaceScanner.advanceCursor(1, 5, 2));
        assertEquals(1, NuclearSurfaceScanner.advanceCursor(4, 5, 2));
    }
}
