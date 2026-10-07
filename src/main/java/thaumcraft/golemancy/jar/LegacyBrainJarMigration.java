package thaumcraft.golemancy.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.CatalogBlockEntity;
import thaumcraft.world.LoadedChunkMigrationQueue;
import java.util.ArrayList;

/** Only storage-free catalogue anchors migrate. Real XP jars retain their tile and contents. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class LegacyBrainJarMigration {
    private static final LoadedChunkMigrationQueue PENDING = new LoadedChunkMigrationQueue();
    private LegacyBrainJarMigration() {}
    @SubscribeEvent public static void loadBrainJarChunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) PENDING.load(level, chunk);
    }
    @SubscribeEvent public static void unloadBrainJarChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.unload(level, event.getChunk().getPos());
    }
    @SubscribeEvent public static void stopBrainJarMigration(ServerStoppedEvent event) { PENDING.stop(event.getServer()); }
    @SubscribeEvent public static void tickBrainJarMigration(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) PENDING.process(level, LegacyBrainJarMigration::repair);
    }
    private static void repair(ServerLevel level, LevelChunk chunk) {
        boolean changed = false;
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
            var state = chunk.getBlockState(pos);
            if (!(state.getBlock() instanceof BrainJarBlock) || !(chunk.getBlockEntity(pos) instanceof CatalogBlockEntity)) continue;
            var replacement = new BrainJarBlockEntity(pos, state);
            chunk.addAndRegisterBlockEntity(replacement); replacement.setChanged(); chunk.setUnsaved(true);
            level.sendBlockUpdated(pos, state, state, 3); changed = true;
        }
        if (changed) {
            var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
            for (var player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) player.connection.send(packet);
        }
    }
}
