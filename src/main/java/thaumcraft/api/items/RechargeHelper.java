package thaumcraft.api.items;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import thaumcraft.equipment.RechargeSupport;

/** Modern-world bridge for the original API. Mutations belong on the server. */
public final class RechargeHelper {
    public static final String NBT_TAG = RechargeSupport.CHARGE_TAG;
    private RechargeHelper() {}
    public static int getCharge(ItemStack stack) { return RechargeSupport.getCharge(stack); }
    public static boolean consumeCharge(ItemStack stack, LivingEntity wearer, int amount) { return RechargeSupport.consumeCharge(stack, wearer, amount); }
    public static float getChargePercentage(ItemStack stack, LivingEntity wearer) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IRechargable)) return -1;
        int max = RechargeSupport.maxCharge(stack, wearer);
        return max == 0 ? 0 : (float)Math.max(0, getCharge(stack)) / max;
    }
    public static float rechargeItem(Level level, ItemStack stack, BlockPos pos, Player wearer, int amount) {
        return level instanceof ServerLevel server ? RechargeSupport.rechargeFromAura(server, stack, pos, wearer, amount) : 0;
    }
    public static float rechargeItemBlindly(ItemStack stack, LivingEntity wearer, int amount) { return RechargeSupport.addCharge(stack, wearer, amount); }
}
