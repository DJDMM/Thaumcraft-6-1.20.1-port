package thaumcraft.world.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.entities.VisualMobEntity;

/** Operational BETA26 Wisp at its original registry ID; the visual base preserves gallery APIs only. */
public final class WispEntity extends VisualMobEntity implements Enemy {
    private static final EntityDataAccessor<String> ASPECT = SynchedEntityData.defineId(WispEntity.class, EntityDataSerializers.STRING);
    private BlockPos flightTarget;
    private int aggroCooldown, attackCounter, previousAttackCounter;

    public WispEntity(EntityType<? extends VisualMobEntity> type, Level level) {
        super(type, level);
        setNoAi(false);
        setNoGravity(true);
        xpReward = 5;
    }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, .1).add(Attributes.FOLLOW_RANGE, 16);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ASPECT, "");
    }
    public String aspectType() { return entityData.get(ASPECT); }
    public void setAspectType(String aspect) { entityData.set(ASPECT, aspect == null ? "" : aspect); }
    public int attackCounter() { return attackCounter; }
    public int previousAttackCounter() { return previousAttackCounter; }
    @Override public int color() { Aspect aspect = Aspect.getAspect(aspectType()); return aspect == null ? 0xFFFF7E : aspect.getColor(); }
    @Override public void tick() {
        super.tick();
        setDeltaMovement(getDeltaMovement().multiply(1, .6000000238418579, 1));
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            setTarget(attacker);
            aggroCooldown = 200;
        }
        return super.hurt(source, amount);
    }
    @Override protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel server) || !RiftEvents.loaded(server, position())) return;
        if (Aspect.getAspect(aspectType()) == null) {
            var choices = random.nextInt(10) != 0 ? Aspect.getPrimalAspects() : Aspect.getCompoundAspects();
            if (!choices.isEmpty()) setAspectType(choices.get(random.nextInt(choices.size())).getTag());
        }
        if (server.getDifficulty() == Difficulty.PEACEFUL) { discard(); return; }
        previousAttackCounter = attackCounter;
        LivingEntity target = getTarget();
        boolean seen = target != null && validTarget(target) && canSee(server, target);
        if (!seen) {
            if (flightTarget != null && (!RiftEvents.loaded(server, Vec3.atCenterOf(flightTarget))
                    || !server.isEmptyBlock(flightTarget) || flightTarget.getY() < server.getMinBuildHeight() + 1
                    || flightTarget.getY() > server.getHeight(Heightmap.Types.MOTION_BLOCKING, flightTarget.getX(), flightTarget.getZ()) + 8))
                flightTarget = null;
            if (flightTarget == null || random.nextInt(30) == 0 || flightTarget.distToCenterSqr(position()) < 4)
                flightTarget = new BlockPos((int)getX() + random.nextInt(7) - random.nextInt(7),
                        (int)getY() + random.nextInt(6) - 2, (int)getZ() + random.nextInt(7) - random.nextInt(7));
            steer(server, new Vec3(flightTarget.getX() + .5, flightTarget.getY() + .1, flightTarget.getZ() + .5));
        } else if (distanceToSqr(target) > 16 * 16 / 2D) {
            steer(server, target.position().add(0, target.getEyeHeight() * .66F, 0));
        }
        if (target != null && !validTarget(target)) { setTarget(null); target = null; }
        --aggroCooldown;
        // The second postfix decrement inside the rare acquisition branch is a release quirk.
        if (random.nextInt(1000) == 0 && (target == null || aggroCooldown-- <= 0)) {
            Player nearest = server.getNearestPlayer(this, 16);
            setTarget(nearest);
            target = nearest;
            if (target != null) aggroCooldown = 50;
        }
        if (isAlive() && target != null && distanceToSqr(target) < 16 * 16) {
            Vec3 delta = target.position().subtract(position());
            float yaw = -(float)Math.atan2(delta.x, delta.z) * 180 / 3.141593F;
            setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
            if (canSee(server, target)) {
                if (++attackCounter == 20) {
                    zap(server, target);
                    attackCounter = -20 + random.nextInt(20);
                }
            } else if (attackCounter > 0) --attackCounter;
        }
    }
    private boolean validTarget(LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && target.level() == level()
                && (!(target instanceof Player player) || !player.getAbilities().invulnerable && !player.isSpectator());
    }
    private boolean canSee(ServerLevel server, LivingEntity target) {
        // Original retaliation can keep a distant attacker. Bound the modern ray and
        // require every intersected chunk before native visibility reads its terrain.
        return RiftEvents.loaded(server,new net.minecraft.world.phys.AABB(getEyePosition(),target.getEyePosition()))
                && hasLineOfSight(target);
    }
    private void steer(ServerLevel server, Vec3 destination) {
        if (!RiftEvents.loaded(server, destination)) { setDeltaMovement(Vec3.ZERO); return; }
        Vec3 delta = destination.subtract(position()), motion = getDeltaMovement();
        setDeltaMovement(motion.add((Math.signum(delta.x) * .5 - motion.x) * .10000000149011612,
                (Math.signum(delta.y) * .699999988079071 - motion.y) * .10000000149011612,
                (Math.signum(delta.z) * .5 - motion.z) * .10000000149011612));
        Vec3 movement = getDeltaMovement();
        float yaw = (float)(Math.atan2(movement.z, movement.x) * 180 / Math.PI) - 90;
        setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
        yBodyRot = getYRot(); yHeadRot = getYRot();
    }
    private void zap(ServerLevel server, LivingEntity target) {
        playSound(sound("zap"), 1, 1.1F);
        Vec3 end = target.getBoundingBox().getCenter(), delta = end.subtract(position());
        var dust = new DustParticleOptions(new Vector3f(((color() >> 16) & 255) / 255F,
                ((color() >> 8) & 255) / 255F, (color() & 255) / 255F), .7F);
        // Native colored particles replace the original custom lightning packet; server attack is unchanged.
        for (int i = 0; i < 16; i++) {
            Vec3 point = position().add(delta.scale(i / 15D));
            server.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        Vec3 motion = target.getDeltaMovement();
        float damage = (float)getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean moving = Math.abs(motion.x) > .10000000149011612 || Math.abs(motion.y) > .10000000149011612
                || Math.abs(motion.z) > .10000000149011612;
        if (random.nextFloat() < (moving ? .4F : .66F)) target.hurt(damageSources().mobAttack(this), moving ? damage : damage + 1);
    }
    @Override public void travel(Vec3 input) {
        if (level() instanceof ServerLevel server && !RiftEvents.loaded(server, getBoundingBox().move(getDeltaMovement()))) setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(.91F));
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource damage) { return false; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public int getMaxSpawnClusterSize() { return 2; }
    @Override public boolean removeWhenFarAway(double distance) { return true; }
    @Override protected boolean shouldDespawnInPeaceful() { return true; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        Aspect aspect = Aspect.getAspect(aspectType());
        if (aspect != null) spawnAtLocation(AspectCrystalItem.create(aspect));
    }
    public boolean canSpawnFromRift() {
        if (!(level() instanceof ServerLevel server) || !RiftEvents.loaded(server, getBoundingBox()) || server.getDifficulty() == Difficulty.PEACEFUL)
            return false;
        if (server.getEntitiesOfClass(WispEntity.class, getBoundingBox().inflate(16), e -> !e.isRemoved()).size() >= 8
                || !server.noCollision(this) || server.containsAnyLiquid(getBoundingBox())) return false;
        BlockPos pos = BlockPos.containing(getX(), getBoundingBox().minY, getZ());
        if (server.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) return false;
        int light = server.isThundering() ? server.getMaxLocalRawBrightness(pos, 10) : server.getMaxLocalRawBrightness(pos);
        return light <= random.nextInt(8);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putString("Type", aspectType()); tag.remove("VisualOnly");
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        CompoundTag migrated = tag.copy();
        if (tag.getBoolean("VisualOnly")) migrated.putBoolean("PersistenceRequired", false);
        super.readAdditionalSaveData(migrated);
        setAspectType(tag.getString("Type")); setNoAi(false); setNoGravity(true);
        flightTarget = null; aggroCooldown = 0; previousAttackCounter = attackCounter = 0;
    }
    private static SoundEvent sound(String name) { return ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", name)); }
    @Override protected SoundEvent getAmbientSound() { return sound("wisplive"); }
    @Override protected SoundEvent getDeathSound() { return sound("wispdead"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.LAVA_EXTINGUISH; }
    @Override protected float getSoundVolume() { return .25F; }
}
