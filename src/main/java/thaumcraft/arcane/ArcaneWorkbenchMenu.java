package thaumcraft.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.world.aura.AuraManager;

/** The result slot is only a preview. Each result click is one server-side transaction. */
public final class ArcaneWorkbenchMenu extends AbstractContainerMenu {
    public static final int PLAYER_START = 16, PLAYER_END = 52;
    private final Container inventory;
    private final SimpleContainer preview = new SimpleContainer(1);
    private final ContainerData data = new SimpleContainerData(10);
    private final ArcaneWorkbenchBlockEntity bench;
    private final Player owner;
    private final BlockPos position;

    public ArcaneWorkbenchMenu(int id, Inventory player, FriendlyByteBuf buffer) { this(id, player, null, buffer.readBlockPos()); }
    public ArcaneWorkbenchMenu(int id, Inventory player, ArcaneWorkbenchBlockEntity bench) { this(id, player, bench, bench.getBlockPos()); }
    private ArcaneWorkbenchMenu(int id, Inventory player, ArcaneWorkbenchBlockEntity bench, BlockPos pos) {
        super(ArcaneModule.MENU.get(), id);
        this.owner = player.player; this.bench = bench; this.position = pos;
        this.inventory = bench == null ? new SimpleContainer(15) : bench;
        addSlot(new Slot(preview, 0, 174, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public ItemStack remove(int count) { return ItemStack.EMPTY; }
        });
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Slot(inventory, x + y * 3, 44 + x * 18, 35 + y * 18));
        for (int i = 0; i < 6; i++) {
            final int primal = i;
            addSlot(new Slot(inventory, 9 + i, 44 + i * 18, 107) {
                @Override public boolean mayPlace(ItemStack stack) { return AspectCrystalItem.matchesPrimal(stack, ArcaneModule.PRIMALS[primal]); }
            });
        }
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(player, 9 + x + y * 9, 35 + x * 18, 143 + y * 18));
        for (int x = 0; x < 9; x++) addSlot(new Slot(player, x, 35 + x * 18, 201));
        addDataSlots(data);
        refresh();
    }

    public int availableVis() { return data.get(0); }
    public int requiredVis() { return data.get(1); }
    public boolean craftable() { return data.get(2) != 0; }
    public boolean hasRecipe() { return data.get(3) != 0; }
    public int crystalCost(int i) { return data.get(4 + i); }

    private void refresh() {
        if (bench == null || !(owner instanceof ServerPlayer player)) return;
        ArcaneRecipe recipe = bench.findRecipe(player);
        preview.setItem(0, recipe == null ? ItemStack.EMPTY : recipe.assemble(bench, player.level().registryAccess()));
        data.set(0, Math.min(32767, (int) AuraManager.getVis(player.serverLevel(), position)));
        data.set(1, ArcaneWorkbenchBlockEntity.visCost(player,recipe));
        data.set(2, bench.canCraft(player, recipe) ? 1 : 0);
        data.set(3, recipe == null ? 0 : 1);
        for (int i = 0; i < 6; i++) data.set(4 + i, recipe == null ? 0 : recipe.crystalCost(i));
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return bench == null ? player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64
                && player.level().getBlockState(position).is(ArcaneModule.WORKBENCH.get()) : bench.stillValid(player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index != 0 && super.canTakeItemForPickAll(stack, slot); }
    @Override public boolean canDragTo(Slot slot) { return slot.index != 0 && super.canDragTo(slot); }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot != 0) { super.clicked(slot, button, type, player); return; }
        // Never let vanilla move the preview: PICKUP_ALL, CLONE, QUICK_CRAFT and number-key
        // swaps otherwise have extraction paths that do not call a normal result onTake.
        if (!(player instanceof ServerPlayer server) || bench == null || !stillValid(player)) return;
        refresh();
        if (!craftable()) return;
        if (type == ClickType.QUICK_MOVE) { quickMoveStack(player, 0); return; }
        ItemStack output = preview.getItem(0);
        if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
            ItemStack carried = getCarried();
            if (!fits(carried, output)) return;
            ItemStack crafted = bench.craft(server);
            if (!crafted.isEmpty()) {
                if (carried.isEmpty()) setCarried(crafted); else carried.grow(crafted.getCount());
            }
        } else if (type == ClickType.SWAP && ((button >= 0 && button < 9) || button == 40)) {
            ItemStack target = player.getInventory().getItem(button);
            if (!fits(target, output)) return;
            ItemStack crafted = bench.craft(server);
            if (!crafted.isEmpty()) {
                if (target.isEmpty()) player.getInventory().setItem(button, crafted); else target.grow(crafted.getCount());
            }
        } else if (type == ClickType.THROW && (button == 0 || button == 1) && getCarried().isEmpty()) {
            ItemStack crafted = bench.craft(server);
            if (!crafted.isEmpty()) player.drop(crafted, true);
        }
        broadcastChanges();
    }
    private static boolean fits(ItemStack destination, ItemStack output) {
        return destination.isEmpty() || ItemStack.isSameItemSameTags(destination, output)
                && destination.getCount() + output.getCount() <= destination.getMaxStackSize();
    }
    private boolean hasPlayerSpace(ItemStack output) {
        int remaining = output.getCount();
        for (int i = PLAYER_START; i < PLAYER_END; i++) {
            Slot slot = slots.get(i); ItemStack current = slot.getItem();
            if (!slot.mayPlace(output)) continue;
            if (current.isEmpty()) remaining -= Math.min(slot.getMaxStackSize(output), output.getMaxStackSize());
            else if (ItemStack.isSameItemSameTags(current, output)) remaining -= Math.max(0, Math.min(slot.getMaxStackSize(output), current.getMaxStackSize()) - current.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        if (index == 0) {
            if (!(player instanceof ServerPlayer server) || bench == null) return ItemStack.EMPTY;
            refresh();
            if (!craftable() || !hasPlayerSpace(preview.getItem(0))) return ItemStack.EMPTY;
            ItemStack crafted = bench.craft(server);
            if (crafted.isEmpty()) return ItemStack.EMPTY;
            ItemStack original = crafted.copy();
            moveItemStackTo(crafted, PLAYER_START, PLAYER_END, true);
            // A mod's crafting callback or a container-item remainder may have filled a
            // destination after capacity was checked. Preserve any leftover output.
            if (!crafted.isEmpty()) player.drop(crafted, false);
            broadcastChanges();
            return original;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        boolean moved;
        if (index < PLAYER_START) moved = moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
        else {
            int crystal = -1;
            for (int i = 0; i < 6; i++) if (AspectCrystalItem.matchesPrimal(stack, ArcaneModule.PRIMALS[i])) { crystal = i; break; }
            moved = crystal >= 0 ? moveItemStackTo(stack, 10 + crystal, 11 + crystal, false) : moveItemStackTo(stack, 1, 10, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        broadcastChanges();
        return original;
    }
}
