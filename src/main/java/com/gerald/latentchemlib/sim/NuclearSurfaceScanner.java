package com.gerald.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class NuclearSurfaceScanner {
    public static final NuclearSurfaceScanner INSTANCE = new NuclearSurfaceScanner();
    private static final int PLAYER_PERIOD_TICKS = 40;
    private static final int BLOCK_CHUNK_RADIUS = 2;
    private static final double DROPPED_ITEM_RADIUS = 96.0;

    private final Map<ResourceKey<Level>, Integer> blockChunkCursor = new HashMap<>();
    private final Map<ResourceKey<Level>, Integer> blockEntityCursor = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % PLAYER_PERIOD_TICKS != 0) return;
        if (!(event.player instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        scanPlayerInventory(level, player);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || event.level.getGameTime() % 20L != 0L) return;
        if (!(event.level instanceof ServerLevel level)) return;
        scanDroppedItems(level);
        scanBlockInventories(level);
    }

    static int advanceCursor(int cursor, int size, int advanced) {
        if (size <= 0) return 0;
        int next = cursor + Math.max(0, advanced);
        return next % size;
    }

    private void scanPlayerInventory(ServerLevel level, ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!NuclearSimulationService.INSTANCE.isNuclearRelevant(stack)) continue;
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return;
            NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
                level,
                player.blockPosition(),
                stack,
                PLAYER_PERIOD_TICKS / 20.0,
                null,
                output -> {
                    if (!inventory.add(output)) player.drop(output, false);
                }
            );
            inventory.setItem(slot, stack);
            inventory.setChanged();
            if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return;
        }
    }

    private void scanDroppedItems(ServerLevel level) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(DROPPED_ITEM_RADIUS);
            List<ItemEntity> items = level.getEntities(EntityType.ITEM, box, item -> item.isAlive() && seen.add(item.getUUID()));
            for (ItemEntity item : items) {
                ItemStack stack = item.getItem();
                if (!NuclearSimulationService.INSTANCE.isNuclearRelevant(stack)) continue;
                if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return;
                NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
                    level,
                    item.blockPosition(),
                    stack,
                    1.0,
                    null,
                    output -> Containers.dropItemStack(level, item.getX(), item.getY(), item.getZ(), output)
                );
                if (stack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(stack);
                }
                if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return;
            }
        }
    }

    private void scanBlockInventories(ServerLevel level) {
        List<ChunkPos> chunks = candidateChunks(level);
        if (chunks.isEmpty()) return;
        ResourceKey<Level> dimension = level.dimension();
        int cursor = Math.floorMod(blockChunkCursor.getOrDefault(dimension, 0), chunks.size());
        int checkedChunks = 0;
        while (checkedChunks < chunks.size()) {
            ChunkPos chunkPos = chunks.get(cursor);
            if (!scanChunkBlockInventories(level, dimension, chunkPos)) {
                blockChunkCursor.put(dimension, cursor);
                return;
            }
            checkedChunks++;
            cursor = advanceCursor(cursor, chunks.size(), 1);
            blockEntityCursor.put(dimension, 0);
        }
        blockChunkCursor.put(dimension, cursor);
    }

    private boolean scanChunkBlockInventories(ServerLevel level, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        ChunkAccess access = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
        if (!(access instanceof LevelChunk chunk)) return true;
        List<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        if (blockEntities.isEmpty()) return true;
        int cursor = Math.floorMod(blockEntityCursor.getOrDefault(dimension, 0), blockEntities.size());
        int checked = 0;
        while (checked < blockEntities.size()) {
            BlockEntity blockEntity = blockEntities.get(cursor);
            if (!scanBlockEntityInventory(level, blockEntity)) {
                blockEntityCursor.put(dimension, cursor);
                return false;
            }
            checked++;
            cursor = advanceCursor(cursor, blockEntities.size(), 1);
        }
        blockEntityCursor.put(dimension, 0);
        return true;
    }

    private boolean scanBlockEntityInventory(ServerLevel level, BlockEntity blockEntity) {
        Optional<IItemHandler> optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty() || !(optional.get() instanceof IItemHandlerModifiable handler)) return true;
        if (!hasRelevantStack(handler)) return true;
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack current = handler.getStackInSlot(slot);
            if (!NuclearSimulationService.INSTANCE.isNuclearRelevant(current)) continue;
            ItemStack working = current.copy();
            List<ItemStack> outputs = new ArrayList<>();
            NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
                level,
                blockEntity.getBlockPos(),
                working,
                1.0,
                NuclearSimulationService.heatSink(blockEntity),
                outputs::add
            );
            if (status == NuclearSimulationService.ProcessStatus.MUTATED) {
                handler.setStackInSlot(slot, working);
                for (ItemStack output : outputs) insertOrDrop(level, blockEntity.getBlockPos(), handler, output);
                blockEntity.setChanged();
                return true;
            }
            if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return false;
        }
        return true;
    }

    private static boolean hasRelevantStack(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (NuclearSimulationService.INSTANCE.isNuclearRelevant(handler.getStackInSlot(slot))) return true;
        }
        return false;
    }

    private static void insertOrDrop(ServerLevel level, BlockPos pos, IItemHandlerModifiable handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        if (!remaining.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, remaining);
        }
    }

    private static List<ChunkPos> candidateChunks(ServerLevel level) {
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> chunks = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dz = -BLOCK_CHUNK_RADIUS; dz <= BLOCK_CHUNK_RADIUS; dz++) {
                for (int dx = -BLOCK_CHUNK_RADIUS; dx <= BLOCK_CHUNK_RADIUS; dx++) {
                    ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                    if (seen.add(chunk.toLong())) chunks.add(chunk);
                }
            }
        }
        return chunks;
    }
}
