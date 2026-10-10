package thaumcraft.world.rift.collapser;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.rift.FluxRiftEntity;

/** Native throwable collision, explosion and the released cubical EntityUtils range. */
public final class CausalityCollapserEntity extends ThrowableItemProjectile {
    public CausalityCollapserEntity(EntityType<? extends CausalityCollapserEntity> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return CatalogModule.ENTRIES.get("causality_collapser").get(); }

    /** BETA26 overrides every launch velocity to .8 while retaining its supplied inaccuracy. */
    @Override public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, .8F, inaccuracy);
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide || isRemoved()) return;
        level().explode(this, getX(), getY(), getZ(), 2F, Level.ExplosionInteraction.TNT);
        // The pinned helper uses intersecting entity bounding boxes in a cube grown3,
        // with no center-distance/spherical filter and no stability prerequisite.
        AABB range = new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(3);
        for (var rift : level().getEntitiesOfClass(FluxRiftEntity.class, range, entity -> !entity.isRemoved()))
            rift.setCollapse(true);
        discard();
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && !isRemoved()) {
            // Native particles replace the two legacy FXDispatcher emitters; original renderer had no mesh.
            for (int index = 0; index < 3; index++) {
                double coefficient = index / 3.;
                double x = xo + (getX() - xo) * coefficient;
                double y = yo + (getY() - yo) * coefficient + getBbHeight() / 2;
                double z = zo + (getZ() - zo) * coefficient;
                level().addParticle(ParticleTypes.FLAME, x, y, z, .0125 * (random.nextFloat() - .5),
                        .0125 * (random.nextFloat() - .5), .0125 * (random.nextFloat() - .5));
                level().addParticle(ParticleTypes.END_ROD, getX() + random.nextGaussian() * .2,
                        getY() + random.nextGaussian() * .2, getZ() + random.nextGaussian() * .2, 0, 0, 0);
            }
        }
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
