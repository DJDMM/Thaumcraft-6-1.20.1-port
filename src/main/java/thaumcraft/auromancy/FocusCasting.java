package thaumcraft.auromancy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import thaumcraft.auromancy.focus.FocusPlan;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.equipment.GearSupport;
import thaumcraft.infusion.InfusionEffects;
import thaumcraft.world.aura.AuraManager;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-owned focus payment/cooldown; delivery continues separately without paying twice. */
public final class FocusCasting {
    public enum Result { INVALID, COOLDOWN, NO_VIS, CAST }
    private static final Map<ServerPlayer, Long> COOLDOWNS = new WeakHashMap<>();
    private FocusCasting() {}
    public static float consumptionModifier(ServerPlayer player) {
        float penalty = player.hasEffect(InfusionEffects.VIS_EXHAUST.get())
                ? (player.getEffect(InfusionEffects.VIS_EXHAUST.get()).getAmplifier() + 1) * .1F : 0;
        return Math.max(.1F, 1 - GearSupport.getTotalVisDiscount(player) + penalty);
    }
    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    public static boolean onCooldown(ServerPlayer player) { return COOLDOWNS.getOrDefault(player, Long.MIN_VALUE) > now(player); }
    public static Result cast(ServerPlayer player, InteractionHand hand) {
        if (player == null || !player.getServer().isSameThread() || !player.isAlive() || player.isSpectator()
                || !FocusSelection.isCaster(player.getItemInHand(hand))) return Result.INVALID;
        var candidate = FocusStacks.readPlan(FocusSelection.installed(player.getItemInHand(hand)));
        if (candidate.isEmpty()) return Result.INVALID;
        if (onCooldown(player)) return Result.COOLDOWN;
        FocusPlan plan = candidate.get();
        // Release behavior: an otherwise valid attempt takes cooldown even when the local aura is insufficient.
        COOLDOWNS.put(player, now(player) + plan.cooldownTicks());
        if (player.connection != null) {
            for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues())
                if (item instanceof CasterItem) player.getCooldowns().addCooldown(item, plan.cooldownTicks());
        }
        float price = plan.castVis() * consumptionModifier(player);
        ServerLevel level = player.serverLevel();
        if (AuraManager.drainVis(level, player.blockPosition(), price, true) < price) return Result.NO_VIS;
        if (AuraManager.drainVis(level, player.blockPosition(), price, false) < price) return Result.NO_VIS;
        if(plan.effect().key().equals(thaumcraft.auromancy.focus.FocusNodeRegistry.FIRE))
            level.playSound(null, player.blockPosition().above(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    1, 1 + (float)(level.random.nextGaussian() * .05));
        else FocusEffects.playCastSound(level,player,plan.effect().key());
        Vec3 source = player.getEyePosition().add(0, -.10000000149011612, 0);
        FocusExecution.resume(player,plan,1,new EntityHitResult(player,player.position()),source,player.getLookAngle().normalize());
        player.swing(hand, true);
        return Result.CAST;
    }

    /** Original entity-first ray: .25 near exclusion, .8 minimum border and a line of sight to its eye. */
    public static HitResult touchTarget(ServerPlayer player) {
        return traceTouch(player, player.getEyePosition().add(0, -.10000000149011612, 0),player.getLookAngle().normalize()).target();
    }
    record Touch(HitResult target, Vec3 trajectory) {}
    static Touch traceTouch(ServerPlayer player, Vec3 start,Vec3 look) {
        ServerLevel level = player.serverLevel();
        double range = player.getBlockReach();
        if (!Double.isFinite(range) || range <= 0 || range > 32) return new Touch(null, start);
        Vec3 end = start.add(look.scale(range));
        if (!loadedRay(level, start, end)) return new Touch(null, start);
        Entity selected = null; Vec3 point = null; double nearest = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().expandTowards(look.scale(range)).inflate(.25),
                e -> !e.isRemoved() && !e.isSpectator() && e.isPickable())) {
            if (start.distanceTo(entity.position()) < .25 || !loadedRay(level, start, entity.getEyePosition())) continue;
            if (level.clip(new ClipContext(start, entity.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            AABB bounds = entity.getBoundingBox().inflate(Math.max(.8F, entity.getPickRadius()));
            var hit = bounds.contains(start) ? java.util.Optional.of(start) : bounds.clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < nearest || nearest==0) { selected = entity; point = hit.get(); nearest = distance; }
            }
        }
        if (selected != null) return new Touch(new EntityHitResult(selected, point), start.add(look.scale(start.distanceTo(selected.position()))));
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.BLOCK ? new Touch(block, block.getLocation()) : new Touch(null, end);
    }
    private static boolean loadedRay(ServerLevel level, Vec3 start, Vec3 end) {
        int x0 = BlockPos.containing(Math.min(start.x, end.x), 0, 0).getX() >> 4;
        int x1 = BlockPos.containing(Math.max(start.x, end.x), 0, 0).getX() >> 4;
        int z0 = BlockPos.containing(0, 0, Math.min(start.z, end.z)).getZ() >> 4;
        int z1 = BlockPos.containing(0, 0, Math.max(start.z, end.z)).getZ() >> 4;
        for (int x=x0;x<=x1;x++) for (int z=z0;z<=z1;z++) if (level.getChunkSource().getChunkNow(x,z)==null) return false;
        return true;
    }
    public static boolean applyFire(ServerLevel level, ServerPlayer caster, HitResult target, int power, int duration) {
        if (!level.getServer().isSameThread() || power < 1 || power > 5 || duration < 0 || duration > 5) return false;
        Vec3 point = target.getLocation();
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 12, .15, .15, .15, .04);
        if (target instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity.fireImmune()) return false;
            var source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FIREBALL), entity, caster);
            entity.hurt(source, 3 + power);
            entity.setSecondsOnFire(1 + duration * duration);
            return true;
        }
        if (target instanceof BlockHitResult block && duration > 0) {
            BlockPos pos = block.getBlockPos().relative(block.getDirection());
            if (!level.hasChunkAt(pos) || !level.isEmptyBlock(pos) || !level.mayInteract(caster, pos)) return false;
            if(!(level.random.nextFloat()<1F))return false;
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1, level.random.nextFloat() * .4F + .8F);
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 11);
            return true;
        }
        return false;
    }
}
