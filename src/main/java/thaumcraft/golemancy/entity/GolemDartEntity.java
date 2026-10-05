package thaumcraft.golemancy.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** BETA26's small vanilla arrow subclass, using the existing golem_dart registry ID. */
public final class GolemDartEntity extends AbstractArrow {
    public GolemDartEntity(EntityType<? extends GolemDartEntity> type, Level level) { super(type, level); }
    @Override protected ItemStack getPickupItem() { return new ItemStack(Items.ARROW); }
}
