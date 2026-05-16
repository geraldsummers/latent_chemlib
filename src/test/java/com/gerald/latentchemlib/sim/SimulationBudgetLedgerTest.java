package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.SchedulerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationBudgetLedgerTest {
    private final SchedulerProfile profile = new SchedulerProfile(2, 3, 4, 5, 6, 7);

    @Test
    void limitsMapEveryBudgetToTheConfiguredProfile() {
        assertEquals(2, SimulationBudgetLedger.limit(SimulationBudget.CLOUD_UPDATES, profile));
        assertEquals(3, SimulationBudgetLedger.limit(SimulationBudget.NEIGHBOR_OPS, profile));
        assertEquals(4, SimulationBudgetLedger.limit(SimulationBudget.ESCAPE_SCANS, profile));
        assertEquals(5, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_INVENTORY_SCANS, profile));
        assertEquals(6, SimulationBudgetLedger.limit(SimulationBudget.STACK_MUTATIONS, profile));
        assertEquals(7, SimulationBudgetLedger.limit(SimulationBudget.HEAT_RADIATION_EMISSIONS, profile));
    }

    @Test
    void spendingTracksPerKeyAndRejectsOverspend() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.CLOUD_UPDATES, 1, profile));
        assertTrue(ledger.trySpend("overworld", SimulationBudget.CLOUD_UPDATES, 1, profile));
        assertFalse(ledger.trySpend("overworld", SimulationBudget.CLOUD_UPDATES, 1, profile));
        assertEquals(2, ledger.spent("overworld", SimulationBudget.CLOUD_UPDATES));

        assertTrue(ledger.trySpend("nether", SimulationBudget.CLOUD_UPDATES, 2, profile));
        assertEquals(2, ledger.spent("nether", SimulationBudget.CLOUD_UPDATES));
    }

    @Test
    void zeroAndNegativeSpendAreNoOps() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, 0, profile));
        assertTrue(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, -10, profile));
        assertEquals(0, ledger.spent("overworld", SimulationBudget.ESCAPE_SCANS));
    }

    @Test
    void resetClearsOneKeyAndResetAllClearsEverything() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.NEIGHBOR_OPS, 3, profile));
        assertTrue(ledger.trySpend("nether", SimulationBudget.NEIGHBOR_OPS, 3, profile));

        ledger.reset("overworld");
        assertEquals(0, ledger.spent("overworld", SimulationBudget.NEIGHBOR_OPS));
        assertEquals(3, ledger.spent("nether", SimulationBudget.NEIGHBOR_OPS));

        ledger.resetAll();
        assertEquals(0, ledger.spent("nether", SimulationBudget.NEIGHBOR_OPS));
    }

    @Test
    void defaultSchedulerProfileKeepsExpectedConservativeBudgets() {
        SchedulerProfile defaults = SchedulerProfile.defaults();
        assertEquals(256, defaults.cloudUpdatesPerSecond());
        assertEquals(768, defaults.neighborOpsPerSecond());
        assertEquals(64, defaults.escapeScansPerSecond());
        assertEquals(96, defaults.nuclearInventoryScansPerSecond());
        assertEquals(32, defaults.stackMutationsPerSecond());
        assertEquals(64, defaults.heatRadiationEmissionsPerSecond());
    }
}
