package thaumcraft.auromancy;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import thaumcraft.auromancy.focus.FocusGraph;

/** BETA26 Break schedules progressive mining; its return is never a payment/harvest result. */
public final class FocusBreakEffect {
    public static final String KEY = "thaumcraft.BREAK";
    private FocusBreakEffect() {}

    /** A linear spell has one target, whose original FocusEngine ordinal is zero. */
    public static boolean apply(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target, Vec3 direction) {
        return apply(level, caster, node, target, direction, 1F, 0);
    }

    /** Original final-power and zero-based target ordinal, retained for later Scatter/Plan integration. */
    public static boolean apply(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target,
                                Vec3 direction, float finalPower, int targetIndex) {
        if (level == null || caster == null || node == null || target == null || !level.getServer().isSameThread()
                || caster.serverLevel() != level || !caster.isAlive() || caster.isSpectator()
                || !KEY.equals(node.key()) || !finite(target.getLocation()) || direction != null && !finite(direction)
                || !Float.isFinite(finalPower) || finalPower <= 0 || finalPower > 16 || targetIndex < 0 || targetIndex > 4096) return false;
        if (node.settings().values().stream().anyMatch(value -> value == null)) return false;
        int power = node.settings().getOrDefault("power", 1);
        int fortune = node.settings().getOrDefault("fortune", 0);
        int silk = node.settings().getOrDefault("silk", 0);
        if (power < 1 || power > 5 || fortune < 0 || fortune > 4 || silk < 0 || silk > 1
                || node.settings().keySet().stream().anyMatch(setting -> !setting.equals("power")
                    && !setting.equals("fortune") && !setting.equals("silk"))) return false;
        if (target instanceof EntityHitResult hit && (hit.getEntity().level() != level || hit.getEntity().isRemoved())) return false;
        // Release returns true even on a non-block target, without damaging it.
        if (!(target instanceof BlockHitResult hit)) return true;
        var pos = hit.getBlockPos();
        if (!FocusEffects.loaded(level, pos)) return true;
        var state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        particles(level, Vec3.atCenterOf(pos));
        // Avoid NaN/infinite lifetime and unloaded-state reads; negative hardness never passed the old tick gate.
        if (state.isAir() || !Float.isFinite(hardness) || hardness < 0) return true;
        float strength = power * finalPower;
        float durability = (float)Math.sqrt(hardness * 100F);
        if (!Float.isFinite(durability)) return true;
        int delay = (int)(durability / strength / 3F * targetIndex);
        float vis = .25F + (silk > 0 ? .25F : 0) + fortune * .1F;
        FocusBreakQueue.enqueue(level, caster, pos, state, strength, durability, delay, silk > 0, fortune, vis);
        return true;
    }

    public static void playCastSound(ServerLevel level, ServerPlayer caster) {
        if (level == null || caster == null || !level.getServer().isSameThread() || caster.serverLevel() != level) return;
        level.playSound(null, caster.blockPosition().above(), SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, .1F,
                2F + (float)(level.random.nextGaussian() * .05000000074505806));
    }
    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    private static void particles(ServerLevel level, Vec3 point) {
        var particle = new DustParticleOptions(new Vector3f(138 / 255F, 75 / 255F, 8 / 255F), 1);
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(point) <= 64 * 64)
            level.sendParticles(player, particle, true, point.x, point.y, point.z, 12, .15, .15, .15, .04);
    }
}
