package thaumcraft.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

public final class GearSupport {
    private GearSupport() {}
    public static int getFinalWarp(ItemStack stack, Player wearer) {
        if (stack.isEmpty()) return 0;
        int warp = stack.getItem() instanceof IWarpingGear gear ? gear.getWarp(stack, wearer) : 0;
        return warp + (stack.hasTag() ? stack.getTag().getByte("TC.WARP") : 0);
    }
    public static int getEquippedWarp(Player wearer) {
        int warp = getFinalWarp(wearer.getMainHandItem(), wearer);
        for (ItemStack stack : wearer.getArmorSlots()) warp += getFinalWarp(stack, wearer);
        return warp;
    }
    public static float getTotalVisDiscount(Player wearer) {
        if (wearer == null) return 0;
        int discount = 0;
        for (ItemStack stack : wearer.getArmorSlots())
            if (stack.getItem() instanceof IVisDiscountGear gear) discount += gear.getVisDiscount(stack, wearer);
        return discount / 100F;
    }
}
