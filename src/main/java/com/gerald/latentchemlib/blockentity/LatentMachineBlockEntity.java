package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.data.ReactionRule;
import com.gerald.latentchemlib.item.ChemicalCellItem;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.NuclearSimulationService;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.antarcticgardens.cna.content.heat.HeatBlockEntity;

public class LatentMachineBlockEntity extends BlockEntity implements HeatBlockEntity {
    private ChemicalState stored = ChemicalState.empty();
    private float heat;

    public LatentMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.MACHINE_ENTITY.get(), pos, blockState);
    }

    public ChemicalState storedState() {
        return stored;
    }

    public void setStoredState(ChemicalState state) {
        stored = state;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = ChemicalState.load(tag.getCompound("stored"));
        heat = tag.getFloat("heat");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stored", stored.save());
        tag.putFloat("heat", heat);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, LatentMachineBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % 20L != 0L) return;
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;
        NuclearSimulationService.StateProcessResult nuclear = NuclearSimulationService.INSTANCE.processChemicalState(
            serverLevel,
            pos,
            entity.stored,
            1.0,
            entity,
            stack -> Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack)
        );
        if (nuclear.budgetExhausted()) return;
        if (nuclear.mutated()) {
            entity.stored = nuclear.state();
            entity.setChanged();
            return;
        }
        Block block = blockState.getBlock();
        if (block == LatentChemlibMod.GAS_CAPTURE.get()) {
            entity.capture(serverLevel);
        } else if (block == LatentChemlibMod.GAS_RELEASE.get()) {
            entity.release(serverLevel);
        } else if (block == LatentChemlibMod.GAS_REACTION_CHAMBER.get()) {
            entity.stored = EmergentMath.chamberAgitation(entity.stored);
            entity.applyReactionRule(serverLevel);
        }
        entity.setChanged();
    }

    public InteractionResult useHeldCell(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel)) return InteractionResult.SUCCESS;

        ChemicalState cellState = ChemicalCellItem.state(stack);
        if (cellState.mass() <= 0.0) {
            if (stored.mass() <= 0.0) return InteractionResult.PASS;
            ChemicalState moved = stored.withMass(Math.min(250.0, stored.mass()));
            stored = stored.withMass(stored.mass() - moved.mass());
            player.setItemInHand(hand, ChemicalCellItem.withState(stack, moved));
            setChanged();
            return InteractionResult.CONSUME;
        }

        if (stored.mass() > 0.0 && !stored.chemicalId().equals(cellState.chemicalId())) return InteractionResult.PASS;
        if (stored.mass() + cellState.mass() > 16_000.0) return InteractionResult.PASS;
        stored = stored.merge(cellState);
        player.setItemInHand(hand, new ItemStack(LatentChemlibMod.SEALED_CHEMICAL_CELL.get()));
        setChanged();
        return InteractionResult.CONSUME;
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

    private void applyReactionRule(ServerLevel level) {
        if (stored.mass() <= 0.0) return;
        for (ReactionRule rule : LatentDataManager.INSTANCE.reactionRules()) {
            if (rule.id().contains(":decay/")) continue;
            if (!rule.matches(stored, heat)) continue;
            heat = Math.max(0.0f, heat - rule.heatCost() + rule.heatEmission());
            stored = rule.apply(stored);
            var item = rule.outputItemValue();
            if (item != null) {
                BlockPos target = worldPosition.above();
                Containers.dropItemStack(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, new ItemStack(item));
            }
            return;
        }
    }

    @Override
    public float getHeat() {
        return heat;
    }

    @Override
    public void addHeat(float heat) {
        this.heat = Math.max(0.0f, this.heat + heat);
    }

    @Override
    public void setHeat(float heat) {
        this.heat = Math.max(0.0f, heat);
    }
}
