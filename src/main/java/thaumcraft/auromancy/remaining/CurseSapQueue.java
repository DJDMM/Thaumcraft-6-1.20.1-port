package thaumcraft.auromancy.remaining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Original effectSap zero-delay server runnable, applied at END before block swap/break work. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseSapQueue {
    private static final Map<ServerLevel, ArrayDeque<LivingEntity>> PENDING = new IdentityHashMap<>();
    private static final Set<ServerLevel> PROCESSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private CurseSapQueue() {}
    static void enqueue(ServerLevel level, LivingEntity living) {
        if (!level.getServer().isSameThread() || living.level() != level) return;
        var pending = PENDING.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        if (pending.size() < 4096) pending.addLast(living);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) process(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        PENDING.keySet().removeIf(level -> level.getServer() == event.getServer());
    }
    public static void process(ServerLevel level) {
        if (!level.getServer().isSameThread() || !PROCESSING.add(level)) return;
        try {
            var batch = PENDING.remove(level); if (batch == null) return;
            for (var living : batch) if (living.level() == level && living.isAlive() && !living.isRemoved()) {
                living.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, true, true));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, true));
                living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 1, true, true));
            }
        } finally { PROCESSING.remove(level); }
    }
}
