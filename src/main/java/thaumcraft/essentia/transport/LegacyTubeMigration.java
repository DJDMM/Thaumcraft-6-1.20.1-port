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
    private static final thaumcraft.world.LoadedChunkMigrationQueue PENDING = new thaumcraft.world.LoadedChunkMigrationQueue();
    private LegacyTubeMigration() {}
    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) PENDING.load(level,chunk);
    }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level) PENDING.unload(level,event.getChunk().getPos());
    }
    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.level instanceof ServerLevel level) PENDING.process(level,LegacyTubeMigration::repairLoadedChunk);
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { PENDING.stop(event.getServer()); }

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
