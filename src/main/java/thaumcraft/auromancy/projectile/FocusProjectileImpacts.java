package thaumcraft.auromancy.projectile;

import net.minecraft.core.BlockPos;
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
import thaumcraft.auromancy.focus.FocusPlan;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Delay-zero BETA26 continuation; a detached64-entry END batch bounds expensive world callbacks. */
@Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FocusProjectileImpacts {
    private static final int MAX_PENDING=4096, BATCH=64;
    private record Impact(ServerPlayer caster,FocusPlan plan,int index,HitResult target,Vec3 source,Vec3 direction,float power,int ordinal) {}
    private static final Map<ServerLevel,ArrayDeque<Impact>> PENDING=new IdentityHashMap<>();
    private FocusProjectileImpacts() {}
    static void enqueue(ServerLevel level,ServerPlayer caster,FocusPlan plan,int index,HitResult target,Vec3 source,Vec3 direction){enqueue(level,caster,plan,index,target,source,direction,1F,0);}
    static void enqueue(ServerLevel level,ServerPlayer caster,FocusPlan plan,int index,HitResult target,Vec3 source,Vec3 direction,float power,int ordinal) {
        if (!level.getServer().isSameThread() || caster.serverLevel()!=level) return;
        var queue=PENDING.computeIfAbsent(level,ignored->new ArrayDeque<>());
        if (queue.size()<MAX_PENDING) queue.addLast(new Impact(caster,plan,index,target,source,direction,power,ordinal));
    }
    static int pending(ServerLevel level) { var queue=PENDING.get(level); return queue==null ? 0 : queue.size(); }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        var queue=PENDING.get(level); if (queue==null) return;
        ArrayDeque<Impact> batch=new ArrayDeque<>();
        for (int i=0;i<BATCH && !queue.isEmpty();i++) batch.addLast(queue.removeFirst());
        if (queue.isEmpty()) PENDING.remove(level);
        for (Impact operation:batch) {
            ServerPlayer caster=operation.caster(); HitResult hit=operation.target();
            if (caster.isRemoved() || !caster.isAlive() || caster.isSpectator() || caster.serverLevel()!=level
                    || !loaded(level,operation.source()) || !loaded(level,hit.getLocation())) continue;
            if (hit instanceof EntityHitResult entity && (entity.getEntity().level()!=level || !entity.getEntity().isAlive()
                    || !loaded(level,entity.getEntity().position()))) continue;
            FocusExecution.resume(caster,operation.plan(),operation.index(),hit,operation.source(),operation.direction(),operation.power(),operation.ordinal());
        }
    }
    private static boolean loaded(ServerLevel level,Vec3 point) {
        if (point==null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return false;
        BlockPos pos=BlockPos.containing(point);
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { PENDING.keySet().removeIf(level->level.getServer()==event.getServer()); }
}
