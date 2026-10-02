package thaumcraft.api.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** BETA26 warp supplied while equipped; it is not permanent player warp. */
public interface IWarpingGear {
    int getWarp(ItemStack stack, Player player);
}
