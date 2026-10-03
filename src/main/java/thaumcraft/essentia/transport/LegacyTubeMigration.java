package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.CatalogBlockEntity;
import thaumcraft.catalog.blocks.CatalogBlocks;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Repairs only the two old catalogue visual anchors without touching their placed blocks. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyTubeMigration {
    // Chunk.Load can run on a worldgen worker before FULL. Only primitive coordinates are queued;
    // neither block entities nor the level's chunk map are read or changed on that callback.
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Integer>> PENDING = new ConcurrentHashMap<>();
    private static final int MAX_WAIT_TICKS = 200, CHUNKS_PER_TICK = 64;
    private LegacyTubeMigration() {}

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            PENDING.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>()).putIfAbsent(chunk.getPos().toLong(), 0);
    }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var queue = PENDING.get(level);
            if (queue != null) queue.remove(event.getChunk().getPos().toLong());
        }
    }
    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Legacy tube migration must run on the server thread");
        var queue = PENDING.get(level);
        if (queue == null) return;
        int visited = 0;
        for (var entry : queue.entrySet()) {
            if (++visited > CHUNKS_PER_TICK) break;
            ChunkPos pos = new ChunkPos(entry.getKey());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                if (entry.getValue() >= MAX_WAIT_TICKS) queue.remove(entry.getKey(), entry.getValue());
                else queue.replace(entry.getKey(), entry.getValue(), entry.getValue() + 1);
                continue;
            }
            // getChunkNow never promotes or loads a chunk. Remove only the coordinate inspected;
            // an off-thread new load can enqueue its own entry for a later END tick.
            queue.remove(entry.getKey(), entry.getValue());
            repairLoadedChunk(level, chunk);
        }
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.keySet().removeIf(level -> level.getServer() == event.getServer());
    }

    private static void repairLoadedChunk(ServerLevel level, LevelChunk chunk) {
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
            BlockState state = chunk.getBlockState(pos);
            if (!(state.getBlock() instanceof TubeBlock block)
                    || !(block.id().equals("tube_valve") || block.id().equals("tube_oneway"))) continue;
            BlockEntity old = chunk.getBlockEntity(pos);
            if (!(old instanceof CatalogBlockEntity) || old.getType() != CatalogBlocks.VISUAL_TILE.get()) continue;
            TubeBlockEntity replacement = TubeBlockEntity.create(pos, state);
            // The old BE had no essence storage or controls. Keep the blockstate orientation and
            // create the empty, open default, rather than accepting arbitrary old NBT as essence.
            chunk.addAndRegisterBlockEntity(replacement);
            replacement.setChanged();
            chunk.setUnsaved(true);
            level.sendBlockUpdated(pos, state, replacement.getBlockState(), 3);
            // No setBlock/destroyBlock: no break callbacks, essence loss, flux, recipes or loot.
        }
    }
}
