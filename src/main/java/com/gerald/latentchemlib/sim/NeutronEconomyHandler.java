package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.smashingmods.chemlib.api.Element;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.antarcticgardens.cna.content.nuclear.NuclearUtil;

public class NeutronEconomyHandler {
    public static final NeutronEconomyHandler INSTANCE = new NeutronEconomyHandler();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % 40 != 0) return;
        Player player = event.player;
        ServerLevel level = (ServerLevel) player.level();
        double flux = 0.0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_INVENTORY_SCANS, 1)) break;
            flux += flux(player.getInventory().getItem(i));
        }
        if (flux > 1.0 && SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.HEAT_RADIATION_EMISSIONS, 1)) {
            NuclearUtil.createRadiation((int) Math.min(24.0, Math.max(1.0, flux / 80.0)), level, player.blockPosition());
        }
    }

    private double flux(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Element element)) return 0.0;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return 0.0;
        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(id.toString());
        ChemicalState state = new ChemicalState(id.toString(), stack.getCount() * Math.max(1.0, element.getAtomicNumber()), 1.0, 293.0, 0.0, 0.0);
        return EmergentMath.neutronFlux(state, traits, 0.0);
    }
}
