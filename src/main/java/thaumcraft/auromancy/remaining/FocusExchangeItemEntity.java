package thaumcraft.auromancy.remaining;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Original EntitySpecialItem: compensated gravity, positive-Y damping and explosion immunity. */
public final class FocusExchangeItemEntity extends ItemEntity {
    public FocusExchangeItemEntity(EntityType<? extends FocusExchangeItemEntity> type, Level level) { super(type, level); }
    @Override public void tick() {
        // Old world increments ticksExisted before onUpdate; the original skips its first update.
        if (tickCount == 0) { tickCount++; return; }
        var motion = getDeltaMovement();
        double y = motion.y > 0 ? motion.y * .8999999761581421 : motion.y;
        setDeltaMovement(motion.x, y + .03999999910593033, motion.z);
        super.tick();
    }
    @Override public boolean hurt(DamageSource source, float amount) { return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
