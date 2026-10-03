package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.CatalogBlockEntity;
import thaumcraft.world.LoadedChunkMigrationQueue;
import java.util.ArrayList;

/** Storage-free visual table migration; server END, fair bounded coordinates, loaded chunks only. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class LegacyFocalMigration {
    private static final LoadedChunkMigrationQueue PENDING=new LoadedChunkMigrationQueue();
    private LegacyFocalMigration(){}
    @SubscribeEvent public static void load(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk)PENDING.load(level,chunk);}
    @SubscribeEvent public static void unload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)PENDING.unload(level,event.getChunk().getPos());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){PENDING.stop(event.getServer());}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)PENDING.process(level,LegacyFocalMigration::repair);}
    private static void repair(ServerLevel level,LevelChunk chunk){
        boolean changed=false;
        for(BlockPos pos:new ArrayList<>(chunk.getBlockEntitiesPos())){
            var state=chunk.getBlockState(pos);
            if(!(state.getBlock() instanceof FocalManipulatorBlock)||!(chunk.getBlockEntity(pos) instanceof CatalogBlockEntity))continue;
            var table=new FocalManipulatorBlockEntity(pos,state);chunk.addAndRegisterBlockEntity(table);table.setChanged();
            chunk.setUnsaved(true);level.sendBlockUpdated(pos,state,state,3);changed=true;
        }
        if(changed){
            var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);
            for(var player:level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false))player.connection.send(packet);
        }
    }
}
