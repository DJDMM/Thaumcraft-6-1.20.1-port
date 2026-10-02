package thaumcraft.api.items;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Original spelling and charge display contract retained for TC6 integrations. */
public interface IRechargable {
    int getMaxCharge(ItemStack stack, LivingEntity wearer);
    EnumChargeDisplay showInHud(ItemStack stack, LivingEntity wearer);
    enum EnumChargeDisplay { NEVER, NORMAL, PERIODIC }
}
