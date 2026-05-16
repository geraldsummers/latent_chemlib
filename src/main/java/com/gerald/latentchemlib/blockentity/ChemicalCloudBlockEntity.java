package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChemicalCloudBlockEntity extends BlockEntity {
    private ChemicalState state = ChemicalState.empty();
    private int age;

    public ChemicalCloudBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.CHEMICAL_CLOUD_ENTITY.get(), pos, blockState);
    }

    public ChemicalState chemicalState() {
        return state;
    }

    public void seed(ChemicalState incoming) {
        if (state.mass() <= 0.0 || state.chemicalId().equals(incoming.chemicalId())) {
            state = state.merge(incoming);
            setChanged();
        }
    }

    public ChemicalState extractMass(double mass) {
        double moved = Math.min(Math.max(0.0, mass), state.mass());
        if (moved <= 0.0) return ChemicalState.empty();
        ChemicalState extracted = state.withMass(moved);
        state = state.withMass(state.mass() - moved);
        setChanged();
        return extracted;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state = ChemicalState.load(tag.getCompound("chemical_state"));
        age = tag.getInt("age");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("chemical_state", state.save());
        tag.putInt("age", age);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, ChemicalCloudBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        int cadence = EmergentMath.updateCadence(entity.state);
        if (cadence <= 0 || serverLevel.getGameTime() % cadence != 0L) return;
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationScheduler.Budget.CLOUD_UPDATES, 1)) return;

        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(entity.state.chemicalId());
        entity.diffuse(serverLevel, traits);
        entity.erode(serverLevel, traits);
        entity.state = EmergentMath.coolAndSettle(entity.state, traits);
        entity.age++;
        if (EmergentMath.shouldDissipate(entity.state, traits)) {
            level.removeBlock(pos, false);
            return;
        }
        entity.setChanged();
    }

    private void diffuse(ServerLevel level, ChemicalTraits traits) {
        double movable = EmergentMath.diffusionMass(state, traits);
        if (movable <= 0.0) return;
        for (Direction direction : Direction.values()) {
            if (movable <= 0.0 || !SimulationScheduler.INSTANCE.trySpend(level, SimulationScheduler.Budget.NEIGHBOR_OPS, 1)) return;
            BlockPos target = worldPosition.relative(direction);
            if (!level.isInWorldBounds(target)) continue;
            BlockState targetState = level.getBlockState(target);
            ChemicalCloudBlockEntity targetEntity = level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud ? cloud : null;
            double targetDensity = targetEntity == null ? 0.0 : targetEntity.state.density();
            double pressure = EmergentMath.pressureGradient(state, targetDensity, traits);
            if (pressure <= 0.0) continue;
            if (targetEntity == null) {
                if (!targetState.isAir() && !targetState.canBeReplaced()) continue;
                level.setBlock(target, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
                targetEntity = level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud ? cloud : null;
            }
            if (targetEntity == null) continue;
            double moved = Math.min(movable, pressure);
            targetEntity.seed(extractMass(moved));
            movable -= moved;
        }
    }

    private void erode(ServerLevel level, ChemicalTraits traits) {
        double flux = EmergentMath.heatFlux(state, traits);
        if (flux <= 0.0) return;
        for (Direction direction : Direction.values()) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationScheduler.Budget.NEIGHBOR_OPS, 1)) return;
            BlockPos target = worldPosition.relative(direction);
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir() || targetState.is(Blocks.BEDROCK)) continue;
            double resistance = Math.max(0.1, targetState.getBlock().defaultDestroyTime());
            if (EmergentMath.erodes(flux, resistance)) {
                level.destroyBlock(target, false);
                state = state.withEnergy(Math.max(0.0, state.energy() - resistance * 140.0));
                return;
            }
        }
    }
}
