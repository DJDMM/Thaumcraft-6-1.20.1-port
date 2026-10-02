package thaumcraft.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

public final class ArcaneWorkbenchBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(15, ItemStack.EMPTY);
    public ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) { super(ArcaneModule.WORKBENCH_TILE.get(), pos, state); }
    @Override public Component getDisplayName() { return Component.translatable("container.thaumcraft.arcane_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ArcaneWorkbenchMenu(id, inventory, this); }
    @Override public int getContainerSize() { return 15; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot < 9 || stack.is(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[slot - 9]).get()); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return !isRemoved() && player.level() == level && Container.stillValidBlockEntity(this, player); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items); }
    @Override public void load(CompoundTag tag) { super.load(tag); items.clear(); ContainerHelper.loadAllItems(tag, items); }

    public ArcaneRecipe findRecipe(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return null;
        return server.getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream()
                .filter(recipe -> recipe.matches(this, server) && recipe.unlocked(player))
                .sorted(java.util.Comparator.comparing(recipe -> recipe.getId().toString())).findFirst().orElse(null);
    }
    public boolean canCraft(ServerPlayer player, ArcaneRecipe recipe) {
        return level instanceof ServerLevel server && !player.isSpectator() && stillValid(player) && recipe != null
                && recipe.unlocked(player) && recipe.matches(this, server) && recipe.hasCrystals(this)
                && AuraManager.getVis(server, worldPosition) >= visCost(player,recipe);
    }

    /** Official BETA26 bytecode multiplies first, then truncates; the decompiled cast is misleading. */
    public static int visCost(Player player,ArcaneRecipe recipe) {
        return recipe==null ? 0 : Math.max(0,(int)(recipe.vis()*(1F-thaumcraft.equipment.GearSupport.getTotalVisDiscount(player))));
    }

    /** Called only after the menu verifies destination capacity. No cached preview is trusted. */
    public ItemStack craft(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return ItemStack.EMPTY;
        ArcaneRecipe recipe = findRecipe(player);
        if (!canCraft(player, recipe)) return ItemStack.EMPTY;
        ItemStack result = recipe.assemble(this, server.registryAccess());
        if (result.isEmpty()) return ItemStack.EMPTY;
        // The aura and inventory are accessed synchronously on the server thread, so another
        // open menu cannot change either between this check and the commit.
        int cost=visCost(player,recipe);
        float drained = AuraManager.drainVis(server, worldPosition, cost, false);
        if (drained < cost) { AuraManager.addVis(server, worldPosition, drained); return ItemStack.EMPTY; }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack input = items.get(slot);
            if (input.isEmpty()) continue;
            ItemStack remainder = input.getCraftingRemainingItem();
            input.shrink(1);
            if (input.isEmpty()) items.set(slot, remainder);
            else if (!remainder.isEmpty()) {
                if (ItemStack.isSameItemSameTags(input, remainder) && input.getCount() + remainder.getCount() <= input.getMaxStackSize()) input.grow(remainder.getCount());
                else if (!player.getInventory().add(remainder)) player.drop(remainder, false);
            }
        }
        for (int i = 0; i < 6; i++) items.get(9 + i).shrink(recipe.crystalCost(i));
        setChanged();
        result.onCraftedBy(server, player, result.getCount());
        net.minecraftforge.event.ForgeEventFactory.firePlayerCraftingEvent(player, result.copy(), this);
        return result;
    }
}
