package thaumcraft.auromancy.media;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import java.util.UUID;

/** Detached paid continuation, never compiled from client spawn appearance data. */
final class PaidFocusContinuation {
    final FocusPlan plan; final int nextIndex,ordinal; final float power; final UUID owner;
    private ServerPlayer cachedCaster;
    PaidFocusContinuation(ServerPlayer caster,FocusPlan plan,int index,float power,int ordinal){
        this(plan,index,power,ordinal,caster.getUUID());cachedCaster=caster;
    }
    private PaidFocusContinuation(FocusPlan plan,int index,float power,int ordinal,UUID owner){
        this.plan=plan;this.nextIndex=index;this.power=power;this.ordinal=ordinal;this.owner=owner;
    }
    ServerPlayer caster(ServerLevel level){
        var player=cachedCaster;
        if(player==null||player.isRemoved())player=level.getServer().getPlayerList().getPlayer(owner);
        // GameTests and server-owned QA fixtures may be unlisted, but still actual level entities.
        if(player==null&&level.getEntity(owner) instanceof ServerPlayer entity)player=entity;
        if(player!=null)cachedCaster=player;
        return player!=null&&!player.isRemoved()&&player.isAlive()&&!player.isSpectator()&&player.serverLevel()==level?player:null;
    }
    boolean bind(ServerPlayer caster){
        if(caster==null||!owner.equals(caster.getUUID()))return false;cachedCaster=caster;return true;
    }
    CompoundTag save(){
        var tag=new CompoundTag();tag.put("Graph",plan.graph().save());tag.putInt("Capacity",plan.maxComplexity());tag.putInt("Next",nextIndex);
        tag.putFloat("Power",power);tag.putInt("Ordinal",ordinal);tag.putUUID("Owner",owner);tag.putUUID("Execution",plan.executionId());return tag;
    }
    static PaidFocusContinuation read(CompoundTag tag,String medium){
        if(!tag.contains("Graph",Tag.TAG_COMPOUND)||!tag.contains("Capacity",Tag.TAG_INT)||!tag.contains("Next",Tag.TAG_INT)
                ||!tag.contains("Power",Tag.TAG_FLOAT)||!tag.contains("Ordinal",Tag.TAG_INT)||!tag.hasUUID("Owner")||!tag.hasUUID("Execution"))return null;
        int capacity=tag.getInt("Capacity"),tier=switch(capacity){case 15->1;case 25->2;case 50->3;default->0;};
        float power=tag.getFloat("Power");int next=tag.getInt("Next"),ordinal=tag.getInt("Ordinal");
        if(tier==0||!Float.isFinite(power)||power<=0||power>16||ordinal<0||ordinal>4096)return null;
        try{
            var compiled=FocusCompiler.compile(FocusGraph.read(tag.getCompound("Graph")),CatalogModule.stack("focus_"+tier),ignored->true);
            if(!compiled.success()||next<1||next>=compiled.plan().graph().nodes().size()||medium(compiled.plan(),next,medium)==null)return null;
            return new PaidFocusContinuation(compiled.plan().withExecutionId(tag.getUUID("Execution")),next,power,ordinal,tag.getUUID("Owner"));
        }catch(IllegalArgumentException ignored){return null;}
    }
    static FocusGraph.Node medium(FocusPlan plan,int next,String key){
        if(plan==null||next<1||next>=plan.graph().nodes().size())return null;
        var child=plan.graph().nodes().get(next);
        return plan.graph().nodes().stream().filter(node->node.id()==child.parent()&&node.key().equals(key)&&node.children().contains(child.id())).findFirst().orElse(null);
    }
    static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    static boolean loaded(ServerLevel level,Vec3 v){
        if(!finite(v))return false;BlockPos p=BlockPos.containing(v);return level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;
    }
    static boolean friendly(Entity owner,Entity target){
        if(owner==null||target==null)return false;
        if(owner==target||owner.hasIndirectPassenger(target)||target.hasIndirectPassenger(owner)||owner.isAlliedTo(target))return true;
        if(target instanceof OwnableEntity pet&&pet.getOwner()==owner)return true;
        return target instanceof Player&&owner.getServer()!=null&&!owner.getServer().isPvpAllowed();
    }
}
