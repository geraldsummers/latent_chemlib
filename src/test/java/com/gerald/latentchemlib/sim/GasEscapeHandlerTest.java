package com.gerald.latentchemlib.sim;

import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasEscapeHandlerTest {
    @Test
    void onlyGasMatterStateCanEscapeAsGas() {
        assertTrue(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.GAS)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.LIQUID)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(new FakeChemical(MatterState.SOLID)));
        assertFalse(GasEscapeHandler.canEscapeAsGas(null));
    }

    private record FakeChemical(MatterState state) implements Chemical {
        @Override
        public Item asItem() {
            return Items.AIR;
        }

        @Override
        public String getChemicalName() {
            return "fake";
        }

        @Override
        public String getAbbreviation() {
            return "Fk";
        }

        @Override
        public MatterState getMatterState() {
            return state;
        }

        @Override
        public String getChemicalDescription() {
            return "";
        }

        @Override
        public List<MobEffectInstance> getEffects() {
            return List.of();
        }

        @Override
        public int getColor() {
            return 0xFFFFFF;
        }
    }
}
