package thaumcraft.essentia.transfuser;

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

/** Old ordinary catalogue blocks acquire their missing stateless ticker on loaded chunks only. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyTransfuserMigration {
    private static final LoadedChunkMigrationQueue PENDING = new LoadedChunkMigrationQueue();
    private LegacyTransfuserMigration() {}
    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) PENDING.load(level, chunk);
    }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.unload(level, event.getChunk().getPos());
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { PENDING.stop(event.getServer()); }
    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) PENDING.process(level, LegacyTransfuserMigration::repair);
    }
    static void repair(ServerLevel level, LevelChunk chunk) {
        boolean changed = false;
        // These blocks were not catalog_visual anchors. A BE-tag-only scan would miss them.
        for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
            var section = chunk.getSection(sectionIndex);
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof EssentiaTransfuserBlock)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (!(state.getBlock() instanceof EssentiaTransfuserBlock)) continue;
                var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                var old = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
                if (old != null && !(old instanceof CatalogBlockEntity)) continue;
                var tile = new EssentiaTransfuserBlockEntity(pos, state);
                chunk.addAndRegisterBlockEntity(tile);
                tile.setChanged();
                chunk.setUnsaved(true);
                level.sendBlockUpdated(pos, state, state, 3);
                changed = true;
            }
        }
        if (changed) {
            var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
            for (var player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) player.connection.send(packet);
        }
    }
}
