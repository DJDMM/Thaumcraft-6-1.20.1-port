package thaumcraft.api.aspects;

import net.minecraft.world.item.ItemStack;

/** TC6 NBT item-container contract. Labels are templates and ignore their stored tag for scanning. */
public interface IEssentiaContainerItem {
    default AspectList getAspects(ItemStack stack) {
        if (!stack.hasTag()) return null;
        AspectList list = new AspectList(); list.readFromNBT(stack.getTag());
        for (Aspect type : list.getAspects()) if (list.getAmount(type) <= 0) list.remove(type);
        return list.size() == 0 ? null : list;
    }
    default void setAspects(ItemStack stack, AspectList list) { list.writeToNBT(stack.getOrCreateTag()); }
    default boolean ignoreContainedAspects() { return false; }
}
