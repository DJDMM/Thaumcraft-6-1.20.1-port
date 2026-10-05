package thaumcraft.golemancy.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Original indirect-magic seeking orb; keeps distance-squared steering and finite guard. */
public final class GolemOrbEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GolemOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(GolemOrbEntity.class, EntityDataSerializers.BOOLEAN);
    public GolemOrbEntity(EntityType<? extends GolemOrbEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(TARGET, -1); entityData.define(RED, false); }
    public boolean red() { return entityData.get(RED); }
    public void setRed(boolean red) { entityData.set(RED, red); }
    public void setTarget(LivingEntity target) { entityData.set(TARGET, target == null ? -1 : target.getId()); }
    public LivingEntity target() { Entity target = level().getEntity(entityData.get(TARGET)); return target instanceof LivingEntity living ? living : null; }
    @Override protected float getGravity() { return 0; }
    @Override public void tick() {
        super.tick();
        if (tickCount > (red() ? 240 : 160)) { discard(); return; }
        LivingEntity target = target();
        if (target != null && target.isAlive()) {
            double distance = distanceToSqr(target);
            if (distance > 1e-12 && Double.isFinite(distance)) {
                Vec3 velocity = getDeltaMovement().add((target.getX() - getX()) / distance * .2,
                        (target.getBoundingBox().minY + target.getBbHeight() * .6 - getY()) / distance * .2,
                        (target.getZ() - getZ()) / distance * .2);
                setDeltaMovement(Mth.clamp((float)velocity.x, -.25F, .25F), Mth.clamp((float)velocity.y, -.25F, .25F), Mth.clamp((float)velocity.z, -.25F, .25F));
            }
        }
        if (level().isClientSide) level().addParticle(red() ? ParticleTypes.FLAME : ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && getOwner() instanceof LivingEntity owner && hit instanceof EntityHitResult entityHit) {
            double damage = owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * (red() ? 1 : .6F);
            if (Double.isFinite(damage) && damage > 0) entityHit.getEntity().hurt(damageSources().indirectMagic(this, owner), (float)damage);
        }
        playSound(GolemEntitySounds.SHOCK.get(), 1, 1 + (random.nextFloat() - random.nextFloat()) * .2F);
        if (level().isClientSide) for (int particle = 0; particle < 12; particle++) level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), random.nextGaussian() * .05, random.nextGaussian() * .05, random.nextGaussian() * .05);
        discard();
    }
    @Override public boolean hurt(DamageSource source, float damage) {
        if (isInvulnerableTo(source)) return false;
        if (source.getEntity() != null) {
            setDeltaMovement(source.getEntity().getLookAngle().scale(.9));
            playSound(GolemEntitySounds.ZAP.get(), 1, 1 + (random.nextFloat() - random.nextFloat()) * .2F);
            return true;
        }
        return false;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // The original stores neither target nor red in NBT; reload starts neutral/unseeking.
        setRed(false); setTarget(null);
    }
}
