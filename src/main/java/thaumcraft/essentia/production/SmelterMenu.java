package thaumcraft.essentia.production;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.scanning.AspectRegistry;

/** Original 2 machine slots, 27 inventory slots and 9 hotbar slots, with 5 live properties. */
public final class SmelterMenu extends AbstractContainerMenu {
    private final Container furnace;
    private final ContainerData data;
    public SmelterMenu(int id, Inventory player, FriendlyByteBuf buffer) { this(id, player, new SimpleContainer(2), new SimpleContainerData(5)); buffer.readBlockPos(); }
    public SmelterMenu(int id, Inventory player, Container furnace, ContainerData data) {
        super(EssentiaProductionModule.SMELTER_MENU.get(), id); this.furnace = furnace; this.data = data;
        checkContainerSize(furnace, 2); checkContainerDataCount(data, 5);
        addSlot(new Slot(furnace, 0, 80, 8) { @Override public boolean mayPlace(ItemStack stack) { return player.player.level().isClientSide || AspectRegistry.getAspects(stack).size() > 0; } });
        // BETA26's GUI fuel Slot is intentionally unrestricted; automation validates fuel.
        addSlot(new Slot(furnace, 1, 80, 48));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(player, column + row*9 + 9, 8 + column*18, 84 + row*18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 8+column*18, 142));
        addDataSlots(data);
    }
    public int cookTime() { return data.get(0); }
    public int burnTime() { return data.get(1); }
    public int currentBurnTime() { return data.get(2); }
    public int totalEssentia() { return data.get(3); }
    public int smeltTime() { return data.get(4); }
    public int scaledCook(int size) { return Math.min(size, Math.max(0, cookTime()*size/Math.max(1,smeltTime()))); }
    public int scaledBurn(int size) { return Math.min(size, Math.max(0, burnTime()*size/(currentBurnTime() == 0 ? 200 : currentBurnTime()))); }
    public int scaledEssentia(int size) { return Math.min(size, Math.max(0,totalEssentia()*size/256)); }
    @Override public boolean stillValid(Player player) { return furnace.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), copy = source.copy();
        if (index < 2) {
            if (!moveItemStackTo(source, 2, 38, false)) return ItemStack.EMPTY;
        } else if (SmelterBlockEntity.fuelTicks(source) > 0) {
            if (!moveItemStackTo(source, 1, 2, false) && !moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (AspectRegistry.getAspects(source).size() > 0) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 29) {
            if (!moveItemStackTo(source, 29, 38, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 2, 29, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source); return copy;
    }
}
