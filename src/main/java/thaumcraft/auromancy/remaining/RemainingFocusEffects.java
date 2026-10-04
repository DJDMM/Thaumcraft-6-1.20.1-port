package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Remaining BETA26 effects: Curse, Exchange and the temporary portable Rift passage. */
public final class RemainingFocusEffects {
    private RemainingFocusEffects() {}
    public static boolean apply(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target,
                                Vec3 direction, float finalPower, int ordinal) {
        if (level == null || caster == null || node == null || target == null || !level.getServer().isSameThread()
                || caster.serverLevel() != level || !caster.isAlive() || caster.isSpectator() || !finite(target.getLocation())
                || direction != null && !finite(direction) || !Float.isFinite(finalPower) || finalPower <= 0 || finalPower > 16
                || ordinal < 0 || ordinal > 4096 || node.settings().values().stream().anyMatch(value -> value == null)) return false;
        if (target instanceof EntityHitResult hit && (hit.getEntity().level() != level || hit.getEntity().isRemoved())) return false;
        return switch (node.key()) {
            case "thaumcraft.CURSE" -> curse(level, caster, node, target, finalPower);
            case "thaumcraft.EXCHANGE" -> exchange(level, caster, node, target);
            case "thaumcraft.RIFT" -> rift(level, caster, node, target, finalPower);
            default -> false;
        };
    }
    private static boolean curse(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target, float finalPower) {
        int power = node.settings().getOrDefault("power", 1), duration = node.settings().getOrDefault("duration", 1);
        if (power < 1 || power > 5 || duration < 1 || duration > 10 || node.settings().keySet().stream()
                .anyMatch(key -> !key.equals("power") && !key.equals("duration"))) return false;
        particles(level, target.getLocation(), 6946821, 64);
        if (target instanceof EntityHitResult hit) {
            var entity = hit.getEntity(); float damage = (1 + power) * finalPower;
            entity.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.INDIRECT_MAGIC), entity, caster), damage);
            if (entity instanceof LivingEntity living) {
                int ticks = 20 * duration, amplifier = Math.max(0, (int)(power * finalPower / 2F));
                living.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amplifier));
                float chance = .85F;
                if (level.random.nextFloat() < chance) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amplifier)); chance -= .15F;
                }
                if (level.random.nextFloat() < chance) {
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, amplifier)); chance -= .15F;
                }
                if (level.random.nextFloat() < chance) {
                    living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks * 2, amplifier)); chance -= .15F;
                }
                if (level.random.nextFloat() < chance) {
                    living.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks * 3, amplifier)); chance -= .15F;
                }
                if (level.random.nextFloat() < chance) living.addEffect(new MobEffectInstance(MobEffects.UNLUCK, ticks * 3, amplifier));
            }
        } else if (target instanceof BlockHitResult hit) {
            float radius = Math.min(8F, 1.5F * power * finalPower); BlockPos origin = hit.getBlockPos();
            BlockPos min = BlockPos.containing(origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius);
            BlockPos max = BlockPos.containing(origin.getX() + radius, origin.getY() + radius, origin.getZ() + radius);
            var sap = CatalogBlocks.block("effect_sap").defaultBlockState();
            for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
                if (Vec3.atCenterOf(mutable).distanceToSqr(hit.getLocation()) > radius * radius || !RiftPassage.loaded(level, mutable)) continue;
                var below = level.getBlockState(mutable); var above = mutable.above();
                if (!below.isCollisionShapeFullBlock(level, mutable) || !RiftPassage.mayChange(level, caster, above)
                        || !level.getBlockState(above).isAir()) continue;
                BlockPos pos = above.immutable(); var previous = level.getBlockState(pos);
                var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
                if (ForgeEventFactory.onBlockPlace(caster, snapshot, Direction.UP) || !RiftPassage.mayChange(level, caster, pos)
                        || level.getBlockState(pos) != previous || level.getBlockState(mutable) != below) continue;
                level.setBlock(pos, sap, 3);
            }
        }
        // BETA26 always returns false even after applying damage/debuffs or terrain sap.
        return false;
    }
    private static boolean exchange(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target) {
        int silk = node.settings().getOrDefault("silk", 0), fortune = node.settings().getOrDefault("fortune", 0);
        if (silk < 0 || silk > 1 || fortune < 0 || fortune > 4 || node.settings().keySet().stream()
                .anyMatch(key -> !key.equals("silk") && !key.equals("fortune")) || !(target instanceof BlockHitResult hit)) return false;
        var hand = FocusSelection.casterHand(caster); if (hand == null) return false;
        var selected = FocusBlockPicker.picked(caster.getItemInHand(hand));
        if (!selected.isEmpty() && RiftPassage.loaded(level, hit.getBlockPos()))
            FocusExchangeQueue.enqueue(level, caster, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()), selected, silk > 0, fortune);
        return true;
    }
    private static boolean rift(ServerLevel level, ServerPlayer caster, FocusGraph.Node node, HitResult target, float finalPower) {
        int depth = node.settings().getOrDefault("depth", 8), duration = node.settings().getOrDefault("duration", 2);
        if (!(depth == 8 || depth == 16 || depth == 24 || depth == 32) || duration < 2 || duration > 10
                || node.settings().keySet().stream().anyMatch(key -> !key.equals("depth") && !key.equals("duration"))
                || !(target instanceof BlockHitResult hit)) return false;
        // BETA26 refuses its dormant Outer Lands dimension. There is no invented modern dimension.
        if (level.dimension().location().equals(RemainingEffectsModule.id("outer_lands"))) {
            var sound = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(RemainingEffectsModule.id("wandfail"));
            if (sound != null) level.playSound(null, hit.getBlockPos(), sound, SoundSource.PLAYERS, 1F, 1F);
            return false;
        }
        float maximumDistance = depth * finalPower; int distance = 0; BlockPos pos = hit.getBlockPos();
        while (distance < maximumDistance) {
            if (!RiftPassage.loaded(level, pos)) break;
            var state = level.getBlockState(pos); float hardness = state.getDestroySpeed(level, pos);
            if (RiftPassage.blacklisted(state) || state.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                    || state.getBlock() instanceof RiftHoleBlock || state.isAir() || !Float.isFinite(hardness) || hardness < 0) break;
            pos = pos.relative(hit.getDirection().getOpposite()); distance++;
        }
        RiftPassage.create(level, caster, hit.getBlockPos(), hit.getDirection(), (byte)(distance + 1), 20 * duration);
        return true;
    }
    public static void playCastSound(ServerLevel level, ServerPlayer caster, String key) {
        if (level == null || caster == null || !level.getServer().isSameThread() || caster.serverLevel() != level) return;
        if (key.equals(FocusNodeRegistry.CURSE)) level.playSound(null, caster.blockPosition().above(), SoundEvents.ELDER_GUARDIAN_CURSE,
                SoundSource.PLAYERS, .15F, 1F + level.random.nextFloat() / 2F);
        else if (key.equals(FocusNodeRegistry.EXCHANGE)) level.playSound(null, caster.blockPosition().above(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, .2F, 2F + (float)(level.random.nextGaussian() * .05000000074505806));
        else if (key.equals(FocusNodeRegistry.RIFT)) level.playSound(null, caster.blockPosition().above(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, .2F, .7F);
    }
    static void particles(ServerLevel level, Vec3 pos, int color, int range) {
        var particle = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F), 1);
        for (var player : level.players()) if (player.distanceToSqr(pos) <= range * range)
            level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, 12, .15, .15, .15, .025);
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
}
