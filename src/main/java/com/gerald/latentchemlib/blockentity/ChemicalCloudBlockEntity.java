package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.block.ChemicalCloudBlock;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.NuclearSimulationService;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.Containers;
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
            syncVisualState();
            setChanged();
        }
    }

    public ChemicalState extractMass(double mass) {
        double moved = Math.min(Math.max(0.0, mass), state.mass());
        if (moved <= 0.0) return ChemicalState.empty();
        ChemicalState extracted = state.withMass(moved);
        state = state.withMass(state.mass() - moved);
        syncVisualState();
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
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;

        NuclearSimulationService.StateProcessResult nuclear = NuclearSimulationService.INSTANCE.processChemicalState(
            serverLevel,
            pos,
            entity.state,
            cadence / 20.0,
            null,
            stack -> Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack)
        );
        if (nuclear.budgetExhausted()) return;
        if (nuclear.mutated()) {
            entity.state = nuclear.state();
            entity.syncVisualState();
            entity.setChanged();
            return;
        }

        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(entity.state.chemicalId());
        entity.diffuse(serverLevel, traits);
        entity.erode(serverLevel, traits);
        entity.state = EmergentMath.coolAndSettle(entity.state, traits);
        entity.age++;
        if (EmergentMath.shouldDissipate(entity.state, traits)) {
            level.removeBlock(pos, false);
            return;
        }
        entity.syncVisualState();
        entity.setChanged();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState current = getBlockState();
        if (!current.hasProperty(ChemicalCloudBlock.DIFFUSION)) return;
        int nextTier = ChemicalCloudBlock.diffusionTier(state);
        if (current.getValue(ChemicalCloudBlock.DIFFUSION) != nextTier) {
            level.setBlock(worldPosition, current.setValue(ChemicalCloudBlock.DIFFUSION, nextTier), 3);
        } else {
            level.sendBlockUpdated(worldPosition, current, current, 3);
        }
    }

    private void diffuse(ServerLevel level, ChemicalTraits traits) {
        double movable = EmergentMath.diffusionMass(state, traits);
        if (movable <= 0.0) return;
        for (Direction direction : Direction.values()) {
            if (movable <= 0.0 || !SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) return;
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
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) return;
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
