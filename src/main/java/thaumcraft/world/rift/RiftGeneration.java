package thaumcraft.world.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;

/** Server-thread consumer of the original last-candidate-per-dimension aura roll. */
public final class RiftGeneration {
    private RiftGeneration() {}
    public static boolean candidate(float flux,float lunarBase,float roll) { return flux>lunarBase*.75 && roll<flux/500F/10F; }
    public static FluxRiftEntity createRift(ServerLevel level,ChunkPos chunk) {
        if (!level.getServer().isSameThread() || RiftModule.WUSS_MODE.get()
                || level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null) return null;
        int x=chunk.getMinBlockX()+level.random.nextInt(16),z=chunk.getMinBlockZ()+level.random.nextInt(16);
        BlockPos surface=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,new BlockPos(x,0,z));
        if (!level.dimensionType().hasSkyLight()) {
            surface=new BlockPos(x,10,z);
            while (!level.isEmptyBlock(surface)) {
                if (surface.getY()>level.getMaxBuildHeight()-5) return null;
                surface=surface.above(level.random.nextInt(5)+1);
            }
        }
        if (surface.getY()>=level.getMaxBuildHeight()-4) return null;
        BlockPos center=surface;
        Vec3 spawnCenter=Vec3.atCenterOf(center);
        AABB search=new AABB(spawnCenter,spawnCenter).inflate(32);
        if (!level.getEntitiesOfClass(FluxRiftEntity.class,search,Entity::isAlive).isEmpty()) return null;
        float flux=AuraManager.getFlux(level,surface);
        double size=Math.sqrt(flux*3F);
        if (size<=5) return null;
        FluxRiftEntity rift=(FluxRiftEntity)VisualEntitiesModule.EFFECTS.get("flux_rift").get().create(level);
        if (rift==null) return null;
        rift.setRiftSeed(level.random.nextInt()); rift.moveTo(x+.5,surface.getY()+.5,z+.5,level.random.nextInt(360),0);
        if (!level.addFreshEntity(rift)) return null;
        rift.setRiftSize((int)size);
        AuraManager.drainFlux(level,surface,(float)size,false); // pay the untruncated sqrt, not the integer display size
        for (var player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,new AABB(surface).inflate(32))) {
            if (KnowledgeStore.recordFact(player,"f_toomuchflux")) {
                player.displayClientMessage(Component.translatable("tc.fluxevent.3").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE,net.minecraft.ChatFormatting.ITALIC),true);
                thaumcraft.research.ResearchNetwork.sync(player);
            }
        }
        return rift;
    }
}
