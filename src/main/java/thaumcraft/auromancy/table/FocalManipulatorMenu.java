package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.auromancy.focus.*;
import thaumcraft.research.PlayerKnowledge;

/** Original one-focus layout with a server-owned, revisioned editor state. */
public final class FocalManipulatorMenu extends AbstractContainerMenu {
    private final FocalManipulatorBlockEntity table;
    private final Player owner;
    private final Inventory inventory;
    private final Container input;
    private final BlockPos position;
    private CompoundTag snapshot = new CompoundTag(), lastSent;
    private PlayerKnowledge knowledge = new PlayerKnowledge();
    private boolean pending;
    private String status = "";

    public FocalManipulatorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, null, buffer.readBlockPos()); }
    public FocalManipulatorMenu(int id, Inventory inventory, FocalManipulatorBlockEntity table) { this(id, inventory, table, table.getBlockPos()); }
    private FocalManipulatorMenu(int id, Inventory inventory, FocalManipulatorBlockEntity table, BlockPos position) {
        super(FocalManipulatorModule.MENU.get(), id);
        this.table = table; this.owner = inventory.player; this.inventory = inventory; this.position = position;
        input = table == null ? new SimpleContainer(1) : table;
        addSlot(new Slot(input, 0, 31, 191) {
            @Override public boolean mayPlace(ItemStack stack) { return FocusStacks.isFocus(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int column = 0; column < 3; column++) for (int row = 0; row < 9; row++)
            addSlot(new Slot(inventory, row + column * 9 + 9, column * 18 - 62, 64 + row * 18));
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++)
            addSlot(new Slot(inventory, column + row * 3, column * 18 - 62, row * 18 + 7));
        if (owner instanceof ServerPlayer server && table != null) setState(table.clientSnapshot(server, null));
    }
    public FocalManipulatorBlockEntity table() { return table; }
    public BlockPos position() { return position; }
    public Inventory playerInventory() { return inventory; }
    public PlayerKnowledge knowledge() { return knowledge; }
    public long revision() { return snapshot.getLong("Revision"); }
    public boolean busy() { return snapshot.getFloat("RemainingVis") > 0; }
    public float remainingVis() { return snapshot.getFloat("RemainingVis"); }
    public int experienceLevel() { return snapshot.getInt("ExperienceLevel"); }
    public boolean creative() { return snapshot.getBoolean("Creative"); }
    public boolean canUse() { return snapshot.getBoolean("CanUse"); }
    public boolean pending() { return pending; }
    public String status() { return status; }
    public String focusName() { return snapshot.getString("FocusName"); }
    public ItemStack focus() { return ItemStack.of(snapshot.getCompound("Focus").copy()); }
    public FocusGraph graph() {
        try { return FocusGraph.read(snapshot.getCompound("Graph")); }
        catch (RuntimeException ignored) { return new FocusGraph(java.util.List.of()); }
    }
    public void setState(CompoundTag state) {
        snapshot = state.copy(); knowledge = PlayerKnowledge.load(state.getCompound("Knowledge"));
        if (state.contains("Result")) { pending = false; status = state.getString("Result"); }
    }
    public void edit(FocusGraph graph, String name) { request(FocalManipulatorNetwork.Action.EDIT, graph.save(), name); }
    public void start() { request(FocalManipulatorNetwork.Action.START, new CompoundTag(), ""); }
    private void request(FocalManipulatorNetwork.Action action, CompoundTag graph, String name) {
        if (pending || !canUse() || busy()) return;
        pending = true; FocalManipulatorNetwork.request(containerId, position, revision(), action, graph, name);
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (table != null && owner instanceof ServerPlayer player) {
            CompoundTag state = table.clientSnapshot(player, null); setState(state);
            if (!state.equals(lastSent)) { lastSent = state.copy(); FocalManipulatorNetwork.send(player, containerId, state); }
        }
    }
    @Override public boolean stillValid(Player player) {
        return table != null ? table.canUse(player) : player != null && player.level().hasChunkAt(position)
                && player.level().getBlockEntity(position) instanceof FocalManipulatorBlockEntity
                && player.distanceToSqr(position.getX()+.5, position.getY()+.5, position.getZ()+.5) <= 64;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (table != null && (player != owner || !table.canUse(player))) return;
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player != owner || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        // The table returns detached stacks: write the final count through setItem so a
        // shift-click removal cancels its paid plan in the same callback.
        ItemStack stack = slot.getItem().copy(), original = stack.copy();
        boolean moved;
        if (index == 0) moved = moveItemStackTo(stack, 1, 37, false);
        else if (FocusStacks.isFocus(stack)) moved = moveItemStackTo(stack, 0, 1, false);
        else moved = index < 28 ? moveItemStackTo(stack, 28, 37, false) : moveItemStackTo(stack, 1, 28, false);
        if (!moved || stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        slot.onTake(player, stack); broadcastChanges(); return original;
    }
}
