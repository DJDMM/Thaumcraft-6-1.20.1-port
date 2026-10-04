package thaumcraft.auromancy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;
import thaumcraft.auromancy.media.FocusMedia;
import thaumcraft.auromancy.remaining.RemainingFocusEffects;
import java.util.*;

/** Server-owned paid tree. Branches and intermediary callbacks never debit cast vis again. */
public final class FocusExecution {
    private FocusExecution(){}
    private record Delivery(HitResult target,Vec3 source,Vec3 direction,int ordinal){}
    private record Seen(long tick,Set<UUID> entities){}
    private static final Map<net.minecraft.server.MinecraftServer,Map<UUID,Seen>> DAMAGE=new WeakHashMap<>();
    public static void resume(ServerPlayer caster,FocusPlan plan,int nextIndex,HitResult target,Vec3 source,Vec3 direction){resume(caster,plan,nextIndex,target,source,direction,1F,0);}
    public static void resume(ServerPlayer caster,FocusPlan plan,int nextIndex,HitResult target,Vec3 source,Vec3 direction,float power){resume(caster,plan,nextIndex,target,source,direction,power,0);}
    public static void resume(ServerPlayer caster,FocusPlan plan,int nextIndex,HitResult target,Vec3 source,Vec3 direction,float power,int ordinal){
        if(caster==null||plan==null||caster.getServer()==null||!caster.getServer().isSameThread()||!caster.isAlive()||caster.isSpectator()
                ||nextIndex<1||nextIndex>=plan.graph().nodes().size()||!finite(source)||!finite(direction)||direction.lengthSqr()<1e-12
                ||!Float.isFinite(power)||power<=0||power>16||ordinal<0||ordinal>4096
                ||target instanceof EntityHitResult e&&e.getEntity().level()!=caster.level())return;
        run(caster,plan,nextIndex,List.of(new Delivery(target,source,direction.normalize(),ordinal)),power,new int[]{4096});
    }
    private static void run(ServerPlayer caster,FocusPlan plan,int index,List<Delivery> inputs,float power,int[] budget){
        if(index<0||index>=plan.graph().nodes().size()||inputs.isEmpty()||budget[0]--<=0)return;
        var node=plan.graph().nodes().get(index);var def=FocusNodeRegistry.get(node.key());
        power*=def.powerMultiplier(node.settings());
        if(FocusNodeRegistry.isSplit(node.key())){
            var deliveries=inputs.stream().map(d->node.key().equals(FocusNodeRegistry.SPLITTARGET)
                    ?new Delivery(d.target(),d.source(),null,d.ordinal()):new Delivery(null,d.source(),d.direction(),d.ordinal())).toList();
            for(int child:node.children())run(caster,plan,plan.indexOf(child),deliveries,power,budget);return;
        }
        int next=node.children().isEmpty()?-1:plan.indexOf(node.children().get(0));
        if(node.key().equals(FocusNodeRegistry.SCATTER)){
            var spread=new ArrayList<Delivery>();int forks=node.settings().get("forks"),angle=node.settings().get("cone");
            for(var d:inputs)if(d.direction()!=null)for(int i=0;i<forks&&spread.size()<4096;i++){
                var random=caster.getRandom();double scale=.007499999832361937*angle;
                Vec3 direction=d.direction().normalize().add(random.nextGaussian()*scale,random.nextGaussian()*scale,random.nextGaussian()*scale).normalize();
                spread.add(new Delivery(null,d.source(),direction,spread.size()));
            }
            run(caster,plan,next,spread,power,budget);return;
        }
        if(node.key().equals(FocusNodeRegistry.PLAN)){
            // Plan supplies one aggregate TARGET array across every incoming trajectory,
            // and no outgoing trajectories. Effect ordinals belong to that whole array.
            var planned=new ArrayList<Delivery>();
            for(var d:inputs)if(d.direction()!=null){
                for(var hit:FocusMedia.planTargets(caster,node,d.source(),d.direction())){
                    if(planned.size()>=4096)break;
                    planned.add(new Delivery(hit,d.source(),null,planned.size()));
                }
            }
            run(caster,plan,next,planned,power,budget);return;
        }
        var outputs=new ArrayList<Delivery>();
        int effectOrdinal=0;
        for(var d:inputs){
            if(budget[0]--<=0)break;
            HitResult target=d.target();Vec3 source=d.source(),direction=d.direction();
            if(def.type()==FocusNodeRegistry.Type.EFFECT){
                if(target==null||target.getType()==HitResult.Type.MISS)continue;
                // Touch can retain trajectories which missed while its TARGET array omits
                // them. BETA26 numbers actual targets densely, not their original forks.
                // A single detached callback already carries its parent's batch ordinal.
                int ordinal=inputs.size()>1?effectOrdinal++:d.ordinal();
                resetSameCast(caster,plan,target);
                if(node.key().equals(FocusNodeRegistry.FIRE))FocusCasting.applyFire(caster.serverLevel(),caster,target,node.settings().get("power"),node.settings().get("duration"),power);
                else if(node.key().equals(FocusNodeRegistry.FLUX)||node.key().equals(FocusNodeRegistry.HEAL))AdvancedFocusEffects.apply(caster.serverLevel(),caster,node,target,direction,power);
                else if(node.key().equals(FocusNodeRegistry.BREAK))FocusBreakEffect.apply(caster.serverLevel(),caster,node,target,direction,power,ordinal);
                else if(Set.of(FocusNodeRegistry.CURSE,FocusNodeRegistry.EXCHANGE,FocusNodeRegistry.RIFT).contains(node.key()))RemainingFocusEffects.apply(caster.serverLevel(),caster,node,target,direction,power,ordinal);
                else FocusEffects.apply(caster.serverLevel(),caster,node,target,direction,power);
                continue;
            }
            if(direction==null)continue;
            if(node.key().equals(FocusNodeRegistry.TOUCH)){
                var trace=FocusCasting.traceTouch(caster,source,direction);source=trace.trajectory();target=trace.target();
            }else if(node.key().equals(FocusNodeRegistry.BOLT)){
                var trace=FocusBoltMedium.trace(caster,plan,source,direction);source=trace.trajectory();target=trace.target();
            }else if(node.key().equals(FocusNodeRegistry.PROJECTILE)){
                FocusProjectileEntity.spawn(caster,plan,next,source,direction,node.settings().get("speed"),node.settings().get("option"),power,d.ordinal());continue;
            }else if(FocusMedia.execute(caster,plan,next,node,target,source,direction,power,d.ordinal()))continue;
            outputs.add(new Delivery(target,source,direction,d.ordinal()));
        }
        if(next>=0)run(caster,plan,next,outputs,power,budget);
    }
    private static void resetSameCast(ServerPlayer caster,FocusPlan plan,HitResult hit){
        if(!(hit instanceof EntityHitResult e)||!(e.getEntity() instanceof LivingEntity living))return;
        long tick=caster.getServer().overworld().getGameTime();var map=DAMAGE.computeIfAbsent(caster.getServer(),ignored->new HashMap<>());
        map.entrySet().removeIf(entry->tick-entry.getValue().tick()>1200);
        if(map.size()>=4096&&!map.containsKey(plan.executionId()))map.clear();
        var seen=map.computeIfAbsent(plan.executionId(),ignored->new Seen(tick,new HashSet<>()));
        if(!seen.entities().add(living.getUUID())&&living.invulnerableTime>0)living.invulnerableTime=0;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
