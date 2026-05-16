package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.smashingmods.chemlib.api.Chemical;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class GasEscapeHandler {
    public static final GasEscapeHandler INSTANCE = new GasEscapeHandler();

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) return;
        tryEscapeItem(itemEntity.getItem(), (ServerLevel) event.getLevel(), itemEntity.blockPosition());
        if (itemEntity.getItem().isEmpty()) itemEntity.discard();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % 20 != 0) return;
        Player player = event.player;
        ServerLevel level = (ServerLevel) player.level();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.ESCAPE_SCANS, 1)) return;
            ItemStack stack = player.getInventory().getItem(i);
            if (tryEscapeItem(stack, level, player.blockPosition())) player.getInventory().setChanged();
        }
    }

    private boolean tryEscapeItem(ItemStack stack, ServerLevel level, BlockPos origin) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Chemical)) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String chemicalId = id.toString();
        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(chemicalId);
        ChemicalState state = new ChemicalState(
            chemicalId,
            stack.getCount() * 125.0,
            Math.max(0.05, stack.getCount() * traits.volatility()),
            293.0,
            0.0,
            stack.getCount() * 40.0
        );
        if (EmergentMath.escapePressure(state, traits, 0.0) <= 0.0) return false;
        spawnCloud(level, origin, state);
        stack.setCount(0);
        return true;
    }

    public static boolean spawnCloud(ServerLevel level, BlockPos origin, ChemicalState state) {
        for (int radius = 0; radius <= 2; radius++) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
                if (!level.isInWorldBounds(pos)) continue;
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
                if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity)) {
                    level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
                }
                if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud) {
                    cloud.seed(state);
                    return true;
                }
            }
        }
        return false;
    }
}
