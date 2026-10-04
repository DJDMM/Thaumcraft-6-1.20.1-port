package thaumcraft.auromancy.media;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.auromancy.FocusExecution;
import java.util.*;

/** BETA26 server callbacks: cloud delay0, mine successive delays, no second cast debit. */
@Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FocusMediaCallbacks {
    private record Operation(long due,PaidFocusContinuation continuation,HitResult target,Vec3 source,Vec3 direction,int ordinal){}
    private static final Map<ServerLevel,ArrayDeque<Operation>> PENDING=new IdentityHashMap<>();
    private FocusMediaCallbacks(){}
    static void enqueue(ServerLevel level,PaidFocusContinuation continuation,HitResult target,Vec3 source,Vec3 direction,int delay,int ordinal){
        if(!level.getServer().isSameThread()||target==null||!PaidFocusContinuation.finite(source)||!PaidFocusContinuation.finite(direction))return;
        var queue=PENDING.computeIfAbsent(level,ignored->new ArrayDeque<>());
        if(queue.size()<8192)queue.addLast(new Operation(level.getGameTime()+Math.max(0,delay),continuation,target,source,direction,ordinal));
    }
    public static int pending(ServerLevel level){var queue=PENDING.get(level);return queue==null?0:queue.size();}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||!level.getServer().isSameThread())return;
        var queue=PENDING.get(level);if(queue==null)return;
        var batch=new ArrayList<Operation>();
        for(var iterator=queue.iterator();iterator.hasNext()&&batch.size()<512;){var op=iterator.next();if(op.due()<=level.getGameTime()){batch.add(op);iterator.remove();}}
        if(queue.isEmpty())PENDING.remove(level);
        for(var op:batch){
            ServerPlayer caster=op.continuation().caster(level);HitResult hit=op.target();
            if(caster==null||!PaidFocusContinuation.loaded(level,op.source())||!PaidFocusContinuation.loaded(level,hit.getLocation()))continue;
            if(hit instanceof EntityHitResult e&&(e.getEntity().isRemoved()||!e.getEntity().isAlive()||e.getEntity().level()!=level))continue;
            Vec3 direction=op.direction().lengthSqr()<1e-12?caster.getLookAngle():op.direction();
            FocusExecution.resume(caster,op.continuation().plan,op.continuation().nextIndex,hit,op.source(),direction,op.continuation().power,op.ordinal());
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){PENDING.remove(level);FocusCloudEntity.clearCooldowns(level);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){PENDING.keySet().removeIf(level->level.getServer()==event.getServer());FocusCloudEntity.clearServerCooldowns(event.getServer());}
}
