package thaumcraft.auromancy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;

/** BETA26 elemental effects. Their return values are not casting/payment success. */
public final class FocusEffects {
    private FocusEffects() {}

    public static boolean apply(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target, Vec3 direction) {
        if (level == null || caster == null || node == null || target == null || !level.getServer().isSameThread()
                || caster.serverLevel() != level || !caster.isAlive() || caster.isSpectator()
                || target.getType() == HitResult.Type.MISS || !finite(target.getLocation())
                || (direction != null && !finite(direction))) return false;
        String key = node.key();
        if (!key.equals(FocusNodeRegistry.AIR) && !key.equals(FocusNodeRegistry.FROST) && !key.equals(FocusNodeRegistry.EARTH)) return false;
        int power = node.settings().getOrDefault("power", 1);
        int duration = node.settings().getOrDefault("duration", 2);
        if (power < 1 || power > 5 || key.equals(FocusNodeRegistry.FROST) && (duration < 2 || duration > 10)
                || node.settings().keySet().stream().anyMatch(setting -> !setting.equals("power")
                    && !(key.equals(FocusNodeRegistry.FROST) && setting.equals("duration")))) return false;
        if (target instanceof EntityHitResult hit && (hit.getEntity().level() != level || hit.getEntity().isRemoved())) return false;
        // Impact particles are native approximations; the original effect packet covered 64 blocks.
        particles(level, target.getLocation(), key);
        if (key.equals(FocusNodeRegistry.AIR)) {
            Vec3 point = target.getLocation();
            level.playSound(null, point.x, point.y, point.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, .5F, .66F);
        }
        if (target instanceof EntityHitResult hit) {
            var entity = hit.getEntity();
            float damage = key.equals(FocusNodeRegistry.AIR) ? 1 + power : key.equals(FocusNodeRegistry.FROST) ? 3 + power : 2 * power;
            // Actual BETA26 quirk: the hit entity, not a projectile, is the immediate source.
            var source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.THROWN), entity, caster);
            entity.hurt(source, damage);
            if (entity instanceof LivingEntity living) {
                if (key.equals(FocusNodeRegistry.AIR)) {
                    double x = direction == null ? -Mth.sin(entity.getYRot() * ((float)Math.PI / 180F)) : -direction.x;
                    double z = direction == null ? Mth.cos(entity.getYRot() * ((float)Math.PI / 180F)) : -direction.z;
                    living.knockback(damage * .25F, x, z);
                } else if (key.equals(FocusNodeRegistry.FROST)) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * duration, (int)(1F + power / 3F)));
                }
            }
            // Frost deliberately returns false even after applying damage and Slowness.
            return !key.equals(FocusNodeRegistry.FROST);
        }
        if (target instanceof BlockHitResult hit) {
            if (key.equals(FocusNodeRegistry.FROST)) freeze(level, caster, hit, power);
            else if (key.equals(FocusNodeRegistry.EARTH)) {
                BlockPos pos = hit.getBlockPos();
                if (loaded(level, pos)) {
                    var state = level.getBlockState(pos);
                    if (state.getDestroySpeed(level, pos) <= 2 * power / 25F) EarthBreakerQueue.enqueue(level, caster, pos, state);
                }
            }
        }
        return false;
    }

    private static void freeze(ServerLevel level, ServerPlayer caster, BlockHitResult hit, int power) {
        float radius = Math.min(16F, 2 * power);
        BlockPos origin = hit.getBlockPos();
        BlockPos min = BlockPos.containing(origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius);
        BlockPos max = BlockPos.containing(origin.getX() + radius, origin.getY() + radius, origin.getZ() + radius);
        var ice = Blocks.FROSTED_ICE.defaultBlockState();
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            if (Vec3.atCenterOf(candidate).distanceToSqr(hit.getLocation()) > radius * radius || !mayChange(level, caster, candidate)) continue;
            var state = level.getBlockState(candidate);
            if (!state.is(Blocks.WATER) || state.getValue(LiquidBlock.LEVEL) != 0
                    || !ice.canSurvive(level, candidate) || !level.isUnobstructed(ice, candidate, CollisionContext.empty())) continue;
            BlockPos pos = candidate.immutable();
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlock(pos, ice, 3)) continue;
            // Modern protection adaptation: cancelled placements restore water before scheduling ice.
            if (ForgeEventFactory.onBlockPlace(caster, before, Direction.DOWN)) { before.restore(true, false); continue; }
            if (level.getBlockState(pos).is(Blocks.FROSTED_ICE)) level.scheduleTick(pos, Blocks.FROSTED_ICE, 60 + level.random.nextInt(61));
        }
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    static boolean mayChange(ServerLevel level, ServerPlayer caster, BlockPos pos) {
        return loaded(level, pos) && level.mayInteract(caster, pos)
                && !caster.blockActionRestricted(level, pos, caster.gameMode.getGameModeForPlayer());
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }

    private static void particles(ServerLevel level, Vec3 point, String key) {
        ParticleOptions particle = key.equals(FocusNodeRegistry.AIR) ? ParticleTypes.CLOUD
                : key.equals(FocusNodeRegistry.FROST) ? ParticleTypes.SNOWFLAKE
                : new DustParticleOptions(new Vector3f(86 / 255F, 192 / 255F, 0), 1);
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(point) <= 64 * 64)
            level.sendParticles(player, particle, true, point.x, point.y, point.z, 12, .15, .15, .15, .04);
    }

    public static void playCastSound(ServerLevel level, ServerPlayer caster, String key) {
        if (level == null || caster == null || !level.getServer().isSameThread() || caster.serverLevel() != level) return;
        if (FocusNodeRegistry.AIR.equals(key))
            level.playSound(null, caster.blockPosition().above(), AuromancySounds.WIND.get(), SoundSource.PLAYERS, .125F, 2);
        else if (FocusNodeRegistry.FROST.equals(key))
            level.playSound(null, caster.blockPosition().above(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, .2F,
                    1F + (float)(level.random.nextGaussian() * .05000000074505806));
        else if (FocusNodeRegistry.EARTH.equals(key))
            level.playSound(null, caster.blockPosition().above(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, .25F,
                    1F + (float)(level.random.nextGaussian() * .05000000074505806));
    }
}
