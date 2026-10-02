package thaumcraft.api.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface IVisDiscountGear {
    int getVisDiscount(ItemStack stack, Player player);
}
