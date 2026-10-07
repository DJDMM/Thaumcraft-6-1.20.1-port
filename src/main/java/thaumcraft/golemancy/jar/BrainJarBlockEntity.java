package thaumcraft.golemancy.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Pinned BETA26 XP jar: all nearby contact orbs are consumed before next-tick capacity clamp. */
public final class BrainJarBlockEntity extends BlockEntity {
    public static final int CAPACITY = 2000;
    private int xp, eatDelay;
    private float rotation, previousRotation, targetRotation;
    private long nextSigh = System.currentTimeMillis() + 1500;

    public BrainJarBlockEntity(BlockPos pos, BlockState state) { super(BrainJarModule.BRAIN_JAR.get(), pos, state); }
    public int xp() { return xp; }
    public int eatDelay() { return eatDelay; }
    public float rotation(float partial) { return previousRotation + wrap(rotation - previousRotation) * partial; }
    private static float wrap(float angle) {
        while (angle >= 3.141593F) angle -= 6.283185F;
        while (angle < -3.141593F) angle += 6.283185F;
        return angle;
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public void readItem(ItemStack stack) {
        if (level != null && level.isClientSide) return;
        xp = stack.hasTag() ? Math.max(0, stack.getTag().getInt("xp")) : 0;
        changed();
    }
    public ItemStack asItemStack() {
        var stack = new ItemStack(getBlockState().getBlock());
        if (xp > 0) stack.getOrCreateTag().putInt("xp", xp);
        return stack;
    }

    /** Activation is ungated, including a held item/sneaking. Zero is a real possible random result. */
    public int releaseXp() {
        eatDelay = 40;
        if (level == null || level.isClientSide || isRemoved()) return 0;
        int released = level.random.nextInt(xp >= 63 ? 64 : xp + 1);
        if (released == 0) return 0;
        xp -= released;
        changed();
        int remaining = released;
        while (remaining > 0) {
            int amount = ExperienceOrb.getExperienceValue(remaining);
            remaining -= amount;
            level.addFreshEntity(new ExperienceOrb(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                    worldPosition.getZ() + .5, amount));
        }
        return released;
    }
    @Nullable public ExperienceOrb closestOrb() {
        if (level == null) return null;
        ExperienceOrb closest = null;
        double distance = Double.MAX_VALUE;
        for (var orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(worldPosition).inflate(8), Entity::isAlive)) {
            double current = orb.distanceToSqr(Vec3.atCenterOf(worldPosition));
            if (current < distance) { distance = current; closest = orb; }
        }
        return closest;
    }
    /** Modern merged orb Count represents several old 1.12 entities, each worth getValue(). */
    public static long totalOrbXp(ExperienceOrb orb) {
        CompoundTag data = new CompoundTag();
        orb.addAdditionalSaveData(data);
        return (long)Math.max(0, orb.getValue()) * Math.max(1, data.getInt("Count"));
    }
    public static void tick(Level level, BlockPos pos, BlockState state, BrainJarBlockEntity jar) {
        if (jar.isRemoved()) return;
        if (jar.xp > CAPACITY) { jar.xp = CAPACITY; jar.changed(); }
        ExperienceOrb orb = jar.xp < CAPACITY ? jar.closestOrb() : null;
        if (orb != null && jar.eatDelay == 0 && !level.isClientSide) {
            Vec3 direction = Vec3.atCenterOf(pos).subtract(orb.position()).scale(1D / 25);
            double distance = direction.length();
            // BETA26 divides by zero at the exact centre. Finite motion is an explicit hardening.
            if (distance > 0 && distance < 1) {
                double strength = (1 - distance) * (1 - distance);
                orb.setDeltaMovement(orb.getDeltaMovement().add(direction.x / distance * strength * .3,
                        direction.y / distance * strength * .5, direction.z / distance * strength * .3));
                orb.hasImpulse = true;
            }
        }
        if (level.isClientSide) jar.animate(level, pos, orb);
        if (jar.eatDelay > 0) { --jar.eatDelay; return; }
        if (level.isClientSide || jar.xp >= CAPACITY) return;
        var touching = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.1), Entity::isAlive);
        long payment = 0;
        for (var nearby : touching) {
            long amount = totalOrbXp(nearby);
            if (amount <= 0) continue;
            payment = Math.min(Integer.MAX_VALUE, payment + amount);
            // Commit removals before sounds/notifications can invoke mod callbacks again.
            nearby.discard();
        }
        if (payment > 0) {
            jar.xp = (int)Math.min(Integer.MAX_VALUE, jar.xp + payment);
            jar.changed();
            for (var consumed : touching) if (consumed.isRemoved())
                level.playSound(null, consumed.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, .1F,
                        (level.random.nextFloat() - level.random.nextFloat()) * .2F + 1);
        }
    }
    private void animate(Level level, BlockPos pos, @Nullable Entity target) {
        previousRotation = rotation;
        if (target == null) {
            target = level.getNearestPlayer(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 6, false);
            if (target != null && nextSigh < System.currentTimeMillis()) {
                level.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, BrainJarModule.BRAIN.get(),
                        SoundSource.AMBIENT, .15F, .8F + level.random.nextFloat() * .4F, false);
                nextSigh = System.currentTimeMillis() + 5000 + level.random.nextInt(25000);
            }
        }
        if (target != null) targetRotation = (float)Math.atan2(target.getZ() - (pos.getZ() + .5), target.getX() - (pos.getX() + .5));
        else targetRotation += .01F;
        rotation = wrap(rotation); targetRotation = wrap(targetRotation);
        rotation += wrap(targetRotation - rotation) * .04F;
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putInt("XP", xp); }
    @Override public void load(CompoundTag tag) { super.load(tag); xp = Math.max(0, tag.getInt("XP")); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
