package thaumcraft.auromancy.media;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import thaumcraft.auromancy.focus.*;
import java.util.List;

/** Execution consumes these media, whose suffix either persists on an entity or fans out over blocks. */
public final class FocusMedia {
    private FocusMedia(){}
    public static boolean execute(ServerPlayer caster,FocusPlan plan,int nextIndex,FocusGraph.Node node,HitResult target,Vec3 source,Vec3 direction,float power,int ordinal){
        switch(node.key()){
            case "thaumcraft.CLOUD" -> FocusCloudEntity.spawn(caster,plan,nextIndex,source,node.settings().get("radius"),node.settings().get("duration"),power,ordinal);
            case "thaumcraft.MINE" -> FocusMineEntity.spawn(caster,plan,nextIndex,source,direction,node.settings().get("target")==1,power,ordinal);
            case "thaumcraft.SPELLBAT" -> SpellBatEntity.spawn(caster,plan,nextIndex,source,node.settings().get("target")==1,power,ordinal);
            default -> {return false;}
        }
        return true;
    }
    /** Plan is aggregated by the execution engine before either target branch runs. */
    public static List<BlockHitResult> planTargets(ServerPlayer caster,FocusGraph.Node node,Vec3 source,Vec3 direction){
        if(node==null||!node.key().equals(FocusNodeRegistry.PLAN))return List.of();
        return FocusPlanArea.targets(caster,source,direction,node.settings().get("method"));
    }
    static boolean validSpawn(ServerPlayer caster,FocusPlan plan,int index,String key,Vec3 source,float power,int ordinal){
        return caster!=null&&caster.getServer()!=null&&caster.getServer().isSameThread()&&caster.isAlive()&&!caster.isSpectator()
                &&PaidFocusContinuation.medium(plan,index,key)!=null&&Float.isFinite(power)&&power>0&&power<=16
                &&ordinal>=0&&ordinal<=4096&&PaidFocusContinuation.loaded(caster.serverLevel(),source);
    }
}
