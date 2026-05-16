package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LatentMachineBlockEntity extends BlockEntity {
    private ChemicalState stored = ChemicalState.empty();

    public LatentMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.MACHINE_ENTITY.get(), pos, blockState);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = ChemicalState.load(tag.getCompound("stored"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stored", stored.save());
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, LatentMachineBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % 20L != 0L) return;
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;
        Block block = blockState.getBlock();
        if (block == LatentChemlibMod.GAS_CAPTURE.get()) {
            entity.capture(serverLevel);
        } else if (block == LatentChemlibMod.GAS_RELEASE.get()) {
            entity.release(serverLevel);
        } else if (block == LatentChemlibMod.GAS_REACTION_CHAMBER.get()) {
            entity.stored = EmergentMath.chamberAgitation(entity.stored);
        }
        entity.setChanged();
    }

    private void capture(ServerLevel level) {
        if (stored.mass() > 16_000.0) return;
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (!(neighbor instanceof ChemicalCloudBlockEntity cloud)) continue;
            ChemicalState cloudState = cloud.chemicalState();
            if (stored.mass() > 0.0 && !stored.chemicalId().equals(cloudState.chemicalId())) continue;
            double amount = Math.min(250.0, Math.max(0.0, cloudState.mass() * 0.25));
            ChemicalState moved = cloud.extractMass(amount);
            stored = stored.merge(moved);
            return;
        }
    }

    private void release(ServerLevel level) {
        if (stored.mass() <= 0.0) return;
        BlockPos target = worldPosition.above();
        if (!level.getBlockState(target).isAir() && !level.getBlockState(target).canBeReplaced()) return;
        if (!(level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity)) {
            level.setBlock(target, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud) {
            ChemicalState moved = stored.withMass(Math.min(250.0, stored.mass()));
            cloud.seed(moved);
            stored = stored.withMass(stored.mass() - moved.mass());
        }
    }
}
