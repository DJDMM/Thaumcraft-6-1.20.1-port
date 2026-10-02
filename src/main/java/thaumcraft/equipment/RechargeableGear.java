package thaumcraft.equipment;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.IRechargable;

public interface RechargeableGear extends IRechargable {
    /** A recharge device decides its rate; wearing an item does not create charge. */
    default int getRechargeRate(ItemStack stack, LivingEntity wearer) { return 0; }
}
