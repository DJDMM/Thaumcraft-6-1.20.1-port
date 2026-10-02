package thaumcraft.api.items;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Legacy hook; TC6 BETA26 has no aura nodes to render. */
@Deprecated
public interface IRevealer {
    boolean showNodes(ItemStack stack, LivingEntity wearer);
}
