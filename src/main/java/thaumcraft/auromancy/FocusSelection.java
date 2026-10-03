package thaumcraft.auromancy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.auromancy.focus.FocusStacks;

/** Physical inventory slots avoid the original sorting-helper collision and never create or drop a focus. */
public final class FocusSelection {
    private FocusSelection() {}
    public static boolean isCaster(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof CasterItem; }
    public static InteractionHand casterHand(Player player) {
        return isCaster(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : isCaster(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }
    public static ItemStack installed(ItemStack caster) {
        if (!isCaster(caster) || !caster.hasTag() || !caster.getTag().contains("focus", 10)) return ItemStack.EMPTY;
        var tag=caster.getTag().getCompound("focus");
        if(!tag.contains("Count",net.minecraft.nbt.Tag.TAG_BYTE))return ItemStack.EMPTY;
        ItemStack focus = ItemStack.of(tag.copy());
        return FocusStacks.isFocus(focus) && focus.getCount() == 1 ? focus : ItemStack.EMPTY;
    }
    public static void setInstalled(ItemStack caster, ItemStack focus) {
        if (!isCaster(caster)) throw new IllegalArgumentException("Not a caster");
        if (focus.isEmpty()) {
            if (caster.hasTag()) caster.getTag().remove("focus");
        } else {
            if (!FocusStacks.isFocus(focus) || focus.getCount() != 1) throw new IllegalArgumentException("Not one focus");
            caster.getOrCreateTag().put("focus", focus.copy().save(new CompoundTag()));
        }
    }
    public static boolean change(ServerPlayer player, InteractionHand hand, int slot, ItemStack expectedCaster, ItemStack expectedFocus) {
        if (player == null || !player.isAlive() || player.isSpectator() || !player.getServer().isSameThread()) return false;
        ItemStack caster = player.getItemInHand(hand);
        if (!isCaster(caster) || !ItemStack.matches(caster, expectedCaster) || slot < -1 || slot >= 36) return false;
        // A broken installed ItemStack must not be silently discarded by swapping it away.
        if (caster.hasTag() && caster.getTag().contains("focus", 10) && installed(caster).isEmpty()) return false;
        ItemStack old = installed(caster);
        if (slot == -1) {
            if (old.isEmpty()) return false;
            int free = player.getInventory().getFreeSlot();
            if (free < 0) return false;
            player.getInventory().setItem(free, old.copy());
            setInstalled(caster, ItemStack.EMPTY);
        } else {
            ItemStack chosen = player.getInventory().getItem(slot);
            if (chosen.getCount() != 1 || !ItemStack.matches(chosen, expectedFocus) || FocusStacks.readPlan(chosen).isEmpty()) return false;
            // Reserve the vacated slot for the returned focus before either inventory is changed.
            player.getInventory().setItem(slot, old.copy());
            setInstalled(caster, chosen.copy());
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        player.serverLevel().playSound(null, player.blockPosition(), AuromancySounds.TICKS.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, .3F, slot == -1 ? .9F : 1F);
        return true;
    }
}
