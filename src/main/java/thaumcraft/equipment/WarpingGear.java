package thaumcraft.equipment;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.IWarpingGear;

public interface WarpingGear extends IWarpingGear {
    int getWarp(ItemStack stack, LivingEntity wearer);
    @Override default int getWarp(ItemStack stack, Player wearer) { return getWarp(stack, (LivingEntity)wearer); }
}
