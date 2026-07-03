package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
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

import java.util.ArrayList;
import java.util.List;

public class GasEscapeHandler {
    public static final GasEscapeHandler INSTANCE = new GasEscapeHandler();
    private static final double ESCAPED_MASS_PER_ITEM = 16.0;
    private static final double ESCAPED_DENSITY_PER_ITEM = 0.18;
    private static final double ESCAPED_MIN_DENSITY = 0.03;
    private static final double ESCAPED_ENERGY_PER_ITEM = 6.0;
    private static final int MAX_SPAWN_CELLS = 6;

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
        if (stack.isEmpty() || !(stack.getItem() instanceof Chemical chemical) || !canEscapeAsGas(chemical)) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String chemicalId = id.toString();
        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(chemicalId);
        ChemicalState state = escapedState(chemicalId, stack.getCount(), traits);
        if (EmergentMath.escapePressure(state, traits, 0.0) <= 0.0) return false;
        if (!spawnCloud(level, origin, state)) return false;
        stack.setCount(0);
        return true;
    }

    static ChemicalState escapedState(String chemicalId, int count, ChemicalTraits traits) {
        int stackCount = Math.max(0, count);
        return new ChemicalState(
            chemicalId,
            stackCount * ESCAPED_MASS_PER_ITEM,
            Math.max(ESCAPED_MIN_DENSITY, stackCount * traits.volatility() * ESCAPED_DENSITY_PER_ITEM),
            293.0,
            0.0,
            stackCount * ESCAPED_ENERGY_PER_ITEM
        );
    }

    public static boolean canEscapeAsGas(Chemical chemical) {
        return chemical != null && chemical.getMatterState() == MatterState.GAS;
    }

    public static boolean spawnCloud(ServerLevel level, BlockPos origin, ChemicalState state) {
        List<BlockPos> targets = new ArrayList<>();
        for (int radius = 0; radius <= 2; radius++) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
                if (!level.isInWorldBounds(pos)) continue;
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
                targets.add(pos.immutable());
                if (targets.size() >= MAX_SPAWN_CELLS) return seedClouds(level, targets, state);
            }
        }
        return seedClouds(level, targets, state);
    }

    private static boolean seedClouds(ServerLevel level, List<BlockPos> targets, ChemicalState state) {
        if (targets.isEmpty() || state.mass() <= 0.0) return false;
        int cells = Math.min(targets.size(), Math.max(1, (int) Math.ceil(state.mass() / 24.0)));
        double totalMass = state.mass();
        double remaining = totalMass;
        for (int i = 0; i < cells; i++) {
            BlockPos pos = targets.get(i);
            if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity)) {
                level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
            }
            if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) continue;
            double share = i == cells - 1 ? remaining : totalMass / cells;
            ChemicalState slice = state.withMass(share);
            cloud.seed(slice);
            remaining -= share;
        }
        return remaining < totalMass;
    }
}
