package thaumcraft.auromancy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;

/** Paid immutable sequence. Intermediary collision resumes this sequence at its next node. */
public final class FocusExecution {
    private FocusExecution(){}
    public static void resume(ServerPlayer caster,FocusPlan plan,int nextIndex,HitResult target,Vec3 source,Vec3 direction){
        if(caster==null||plan==null||!caster.getServer().isSameThread()||nextIndex<1||nextIndex>=plan.graph().nodes().size()
                ||source==null||direction==null||!finite(source)||!finite(direction)||direction.lengthSqr()<1e-12
                ||target instanceof net.minecraft.world.phys.EntityHitResult entityHit&&entityHit.getEntity().level()!=caster.level())return;
        direction=direction.normalize();
        for(int i=nextIndex;i<plan.graph().nodes().size();i++){
            var node=plan.graph().nodes().get(i);
            if(node.key().equals(FocusNodeRegistry.TOUCH)){
                var trace=FocusCasting.traceTouch(caster,source,direction);source=trace.trajectory();target=trace.target();
            }else if(node.key().equals(FocusNodeRegistry.PROJECTILE)){
                FocusProjectileEntity.spawn(caster,plan,i+1,source,direction,node.settings().get("speed"),node.settings().get("option"));
                return;
            }else if(target!=null){
                if(node.key().equals(FocusNodeRegistry.FIRE))FocusCasting.applyFire(caster.serverLevel(),caster,target,node.settings().get("power"),node.settings().get("duration"));
                else FocusEffects.apply(caster.serverLevel(),caster,node,target,direction);
            }
        }
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
