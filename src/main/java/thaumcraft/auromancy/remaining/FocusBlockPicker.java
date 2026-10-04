package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.catalog.CatalogModule;

/** BETA26 IFocusBlockPicker, selected item stored on the physical caster under original "picked". */
public final class FocusBlockPicker {
    private FocusBlockPicker() {}
    public static boolean isPicker(ItemStack caster) {
        if (!FocusSelection.isCaster(caster)) return false;
        var plan = FocusStacks.readPlan(FocusSelection.installed(caster));
        return plan.isPresent() && plan.get().graph().nodes().stream().anyMatch(node -> node.key().equals(FocusNodeRegistry.EXCHANGE));
    }
    public static ItemStack picked(ItemStack caster) {
        if (!isPicker(caster) || !caster.hasTag() || !caster.getTag().contains("picked", Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        var tag = caster.getTag().getCompound("picked");
        if (!tag.contains("id", Tag.TAG_STRING) || !tag.contains("Count", Tag.TAG_BYTE)) return ItemStack.EMPTY;
        ItemStack out = ItemStack.of(tag.copy());
        if (out.isEmpty() || out.getCount() != 1) return ItemStack.EMPTY;
        return out;
    }
    /** Selection is free and never grants research, costs vis, or swaps any block. */
    public static boolean pick(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        if (player == null || hand == null || pos == null || !player.getServer().isSameThread() || !player.isAlive()
                || player.isSpectator() || !player.isShiftKeyDown()) return false;
        var level = player.serverLevel(); ItemStack caster = player.getItemInHand(hand);
        if (!isPicker(caster) || !RiftPassage.loaded(level, pos) || level.getBlockEntity(pos) != null
                || player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 9 * 9) return false;
        var state = level.getBlockState(pos); if (state.isAir() || state.hasBlockEntity()) return false;
        ItemStack selected = new ItemStack(state.getBlock());
        // Original getSilkTouchDrop chooses a state-dependent single stack; ordinary non-BE
        // modern loot supplies the corresponding colour/variant and preserves leaf/glass blocks.
        var silk = CatalogModule.stack("enchanted_placeholder"); silk.enchant(Enchantments.SILK_TOUCH, 1);
        var drops = Block.getDrops(state, level, pos, null, player, silk);
        if (!drops.isEmpty() && !drops.get(0).isEmpty()) selected = drops.get(0).copy();
        if (selected.isEmpty()) return false;
        selected.setCount(1);
        if (player.getItemInHand(hand) != caster || !isPicker(caster) || level.getBlockState(pos) != state
                || level.getBlockEntity(pos) != null) return false;
        caster.getOrCreateTag().put("picked", selected.save(new CompoundTag()));
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        return true;
    }
}
