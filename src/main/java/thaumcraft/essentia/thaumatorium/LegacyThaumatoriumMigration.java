package thaumcraft.essentia.thaumatorium;

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

/** Loaded-only catalogue tile repair; missing anchors acquire BEs without changing blocks or inventory. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class LegacyThaumatoriumMigration {
    private static final LoadedChunkMigrationQueue PENDING=new LoadedChunkMigrationQueue();
    private LegacyThaumatoriumMigration() {}
    @SubscribeEvent public static void loadThaumatoriumChunk(ChunkEvent.Load event) {if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk)PENDING.load(level,chunk);}
    @SubscribeEvent public static void unloadThaumatoriumChunk(ChunkEvent.Unload event) {if(event.getLevel() instanceof ServerLevel level)PENDING.unload(level,event.getChunk().getPos());}
    @SubscribeEvent public static void stopThaumatoriumMigration(ServerStoppedEvent event) {PENDING.stop(event.getServer());}
    @SubscribeEvent public static void tickThaumatoriumMigration(TickEvent.LevelTickEvent event) {if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)PENDING.process(level,LegacyThaumatoriumMigration::repair);}
    private static void repair(ServerLevel level,LevelChunk chunk) {
        boolean changed=false;
        // Old thaumatorium was an ordinary catalogue model, and may have no old tile at all.
        for(int sectionIndex=0;sectionIndex<chunk.getSectionsCount();sectionIndex++) {
            var section=chunk.getSection(sectionIndex);
            if(section.hasOnlyAir()||!section.maybeHas(state->state.getBlock() instanceof ThaumatoriumBlock))continue;
            int baseY=chunk.getSectionYFromSectionIndex(sectionIndex)*16;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            BlockPos pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);var state=section.getBlockState(x,y,z);
            if(!(state.getBlock() instanceof ThaumatoriumBlock block))continue;
            var old=chunk.getBlockEntity(pos);
            if(old!=null&&!(old instanceof CatalogBlockEntity))continue;
            var tile=block.isTop()?new ThaumatoriumTopBlockEntity(pos,state):new ThaumatoriumBlockEntity(pos,state);
            chunk.addAndRegisterBlockEntity(tile);tile.setChanged();chunk.setUnsaved(true);level.sendBlockUpdated(pos,state,state,3);changed=true;
            }
        }
        if(changed) {var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);for(var player:level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false))player.connection.send(packet);}
    }
}
