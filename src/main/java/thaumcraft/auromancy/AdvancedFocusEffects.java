package thaumcraft.auromancy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import thaumcraft.auromancy.focus.FocusGraph;

/** BETA26 Flux/Heal effects at final power 1. Effect returns do not report payment success. */
public final class AdvancedFocusEffects {
    private static final String FLUX = "thaumcraft.FLUX", HEAL = "thaumcraft.HEAL";
    private AdvancedFocusEffects() {}

    public static boolean apply(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target, Vec3 direction) {
        return apply(level,caster,node,target,direction,1F);
    }
    public static boolean apply(ServerLevel level,ServerPlayer caster,FocusGraph.Node node,HitResult target,Vec3 direction,float finalPower){
        if (!Float.isFinite(finalPower)||finalPower<=0||finalPower>16||level == null || caster == null || node == null || target == null || !level.getServer().isSameThread()
                || caster.serverLevel() != level || !caster.isAlive() || caster.isSpectator()
                || target.getType() == HitResult.Type.MISS || !finite(target.getLocation())
                || direction != null && !finite(direction)
                || !FocusEffects.loaded(level, BlockPos.containing(target.getLocation()))) return false;
        String key = node.key();
        if (!FLUX.equals(key) && !HEAL.equals(key)) return false;
        if (node.settings().entrySet().stream().anyMatch(setting -> !"power".equals(setting.getKey()) || setting.getValue() == null)) return false;
        int power = node.settings().getOrDefault("power", 1);
        if (power < 1 || power > 5) return false;
        if (target instanceof EntityHitResult hit && (hit.getEntity().level() != level || hit.getEntity().isRemoved()
                || !finite(hit.getEntity().position()) || !FocusEffects.loaded(level, hit.getEntity().blockPosition()))) return false;

        // Original impact packets reach players within 64 blocks even for a block/nonliving hit.
        particles(level, target.getLocation(), key);
        if (target instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (FLUX.equals(key)) {
                // Confirmed BETA26 quirk: Flux uses the hit entity as its immediate source.
                entity.hurt(magic(level, entity, caster), (3 + power)*finalPower);
            } else if (entity instanceof LivingEntity living) {
                if (living.isInvertedHealAndHarm()) {
                    // Heal has different attribution: the caster is BOTH immediate and true source.
                    living.hurt(magic(level, caster, caster), power * finalPower * 1.5F);
                } else {
                    living.heal(power*finalPower);
                }
            }
        }
        // Both effects always return false, including accepted damage/healing.
        return false;
    }

    private static DamageSource magic(ServerLevel level, Entity immediate, ServerPlayer caster) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.INDIRECT_MAGIC), immediate, caster);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static void particles(ServerLevel level, Vec3 point, String key) {
        var particle = new DustParticleOptions(FLUX.equals(key) ? new Vector3f(.5F, 0, .5F) : new Vector3f(1, 1, 1), 1);
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(point) <= 64 * 64)
            level.sendParticles(player, particle, true, point.x, point.y, point.z, 12, .15, .15, .15, .04);
    }

    public static void playCastSound(ServerLevel level, ServerPlayer caster, String key) {
        if (level == null || caster == null || !level.getServer().isSameThread() || caster.serverLevel() != level
                || !caster.isAlive() || caster.isSpectator() || !FLUX.equals(key) && !HEAL.equals(key)) return;
        level.playSound(null, caster.blockPosition().above(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS,
                2F, 2F + (float)(level.random.nextGaussian() * .10000000149011612));
    }
}
