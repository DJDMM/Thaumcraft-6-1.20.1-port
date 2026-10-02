package thaumcraft.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.IRechargable;
import thaumcraft.world.aura.AuraManager;

/** BETA26 tc.charge storage and whole-unit aura recharge. */
public final class RechargeSupport {
    public static final String CHARGE_TAG = "tc.charge";
    private RechargeSupport() {}
    public static int getCharge(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IRechargable)) return -1;
        return stack.hasTag() ? Math.max(0, stack.getTag().getInt(CHARGE_TAG)) : 0;
    }
    public static int maxCharge(ItemStack stack, LivingEntity wearer) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IRechargable item ? Math.max(0, item.getMaxCharge(stack, wearer)) : 0;
    }
    public static boolean consumeCharge(ItemStack stack, LivingEntity wearer, int amount) {
        if (stack == null || stack.isEmpty() || amount < 0 || !(stack.getItem() instanceof IRechargable) || getCharge(stack) < amount) return false;
        if (amount > 0) stack.getOrCreateTag().putInt(CHARGE_TAG, getCharge(stack) - amount);
        return true;
    }
    public static int addCharge(ItemStack stack, LivingEntity wearer, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0 || !(stack.getItem() instanceof IRechargable)) return 0;
        int old = getCharge(stack), added = Math.max(0, Math.min(amount, maxCharge(stack, wearer) - old));
        if (added > 0) stack.getOrCreateTag().putInt(CHARGE_TAG, old + added);
        return added;
    }
    public static int rechargeFromAura(ServerLevel level, ItemStack stack, BlockPos pos, Player wearer, int amount) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IRechargable)) return 0;
        if(wearer instanceof net.minecraft.server.level.ServerPlayer player
                && thaumcraft.research.KnowledgeStore.get(player).isResearchCompleteStrict("AURAPRESERVE")
                && AuraManager.getVis(level,pos)/AuraManager.getAuraBase(level,pos)<.1F) return 0;
        int requested = Math.max(0, Math.min(amount, maxCharge(stack, wearer) - getCharge(stack)));
        if (requested == 0) return 0;
        // BETA26 truncates the drained float to charge units, including at low aura.
        int drained = (int)AuraManager.drainVis(level, pos, requested, false);
        return addCharge(stack, wearer, drained);
    }
}
