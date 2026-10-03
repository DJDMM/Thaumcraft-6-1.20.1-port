package thaumcraft.infusion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded END-tick upgrade of the storage-free catalogue matrix, never a worker-thread world mutation. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class LegacyInfusionMigration {
    private static final thaumcraft.world.LoadedChunkMigrationQueue PENDING=new thaumcraft.world.LoadedChunkMigrationQueue();
    private LegacyInfusionMigration() {}
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk)PENDING.load(level,chunk);
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)PENDING.unload(level,event.getChunk().getPos());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { PENDING.stop(event.getServer()); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)PENDING.process(level,LegacyInfusionMigration::repair);
    }
    private static void repair(ServerLevel level,LevelChunk chunk) {
        var pos=chunk.getPos();
            boolean upgraded=false;
            for(BlockPos matrix:new ArrayList<>(chunk.getBlockEntitiesPos())) {
                var state=chunk.getBlockState(matrix);
                if(!(state.getBlock() instanceof InfusionMatrixBlock)||!(chunk.getBlockEntity(matrix) instanceof CatalogBlockEntity))continue;
                var replacement=new InfusionMatrixBlockEntity(matrix,state);chunk.addAndRegisterBlockEntity(replacement);
                replacement.setChanged();chunk.setUnsaved(true);level.sendBlockUpdated(matrix,state,state,3);
                upgraded=true;
            }
            // A normal BE update cannot change a client tile's type. Rebuild this already tracked
            // chunk once so a client that received the old catalogue tile gets the operational BE.
            if(upgraded) {
                var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);
                for(var player:level.getChunkSource().chunkMap.getPlayers(pos,false))player.connection.send(packet);
            }
    }
}
