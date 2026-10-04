package thaumcraft.essentia.centrifuge;

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

/** Empty catalogue anchors only; fair END migration preserves working device contents. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class LegacyCentrifugeMigration {
    private static final LoadedChunkMigrationQueue PENDING=new LoadedChunkMigrationQueue();
    private LegacyCentrifugeMigration() {}
    @SubscribeEvent public static void loadCentrifugeChunk(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)PENDING.load(level,chunk);
    }
    @SubscribeEvent public static void unloadCentrifugeChunk(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)PENDING.unload(level,event.getChunk().getPos());
    }
    @SubscribeEvent public static void stopCentrifugeMigration(ServerStoppedEvent event) { PENDING.stop(event.getServer()); }
    @SubscribeEvent public static void tickCentrifugeMigration(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.level instanceof ServerLevel level)PENDING.process(level,LegacyCentrifugeMigration::repair);
    }
    private static void repair(ServerLevel level, LevelChunk chunk) {
        boolean changed=false;
        for(BlockPos pos:new ArrayList<>(chunk.getBlockEntitiesPos())) {
            var state=chunk.getBlockState(pos);
            if(!(state.getBlock() instanceof CentrifugeBlock) || !(chunk.getBlockEntity(pos) instanceof CatalogBlockEntity))continue;
            var replacement=new CentrifugeBlockEntity(pos,state);
            chunk.addAndRegisterBlockEntity(replacement); replacement.setChanged(); chunk.setUnsaved(true);
            level.sendBlockUpdated(pos,state,state,3); changed=true;
        }
        if(changed) {
            // A BE update packet alone cannot change the client-side tile's class.
            var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);
            for(var player:level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false))player.connection.send(packet);
        }
    }
}
