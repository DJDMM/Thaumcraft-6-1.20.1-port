package thaumcraft.research.theory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.Items;
import thaumcraft.research.PlayerKnowledge;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ResearchTableMenu extends AbstractContainerMenu {
    public static final int PLAYER_START = 2, PLAYER_END = 38;
    private final ResearchTableBlockEntity table;
    private final Player owner;
    private final Inventory playerInventory;
    private final BlockPos position;
    private final Container inventory;
    private CompoundTag lastSent;
    private TheorySession session;
    private PlayerKnowledge knowledge = new PlayerKnowledge();
    private long revision;
    private int inspirationAvailable = 5;
    private int experienceLevel;
    private boolean canUse;
    private boolean pending;
    private Set<String> aids = Set.of();
    private String status = "";

    public ResearchTableMenu(int id, Inventory player, FriendlyByteBuf buffer) { this(id, player, null, buffer.readBlockPos()); }
    public ResearchTableMenu(int id, Inventory player, ResearchTableBlockEntity table) { this(id, player, table, table.getBlockPos()); }
    private ResearchTableMenu(int id, Inventory player, ResearchTableBlockEntity table, BlockPos position) {
        super(TheoryModule.MENU.get(), id);
        this.owner = player.player; this.playerInventory = player; this.table = table; this.position = position;
        this.inventory = table == null ? new SimpleContainer(2) : table;
        addSlot(new Slot(inventory, 0, 16, 15) {
            @Override public boolean mayPlace(ItemStack stack) { return inventoryAccessible() && stack.is(TheoryModule.SCRIBING_TOOLS.get()); }
            @Override public boolean mayPickup(Player player) { return inventoryAccessible(); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(inventory, 1, 224, 16) {
            @Override public boolean mayPlace(ItemStack stack) { return inventoryAccessible() && stack.is(Items.PAPER); }
            @Override public boolean mayPickup(Player player) { return inventoryAccessible(); }
        });
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(player, 9 + x + y * 9, 77 + x * 18, 190 + y * 18));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Slot(player, x + y * 3, 20 + x * 18, 190 + y * 18));
        if (owner instanceof ServerPlayer server && table != null) setState(table.clientSnapshot(server, null));
    }
    public static ResearchTableMenu preview(int id, Inventory owner, CompoundTag state) {
        ResearchTableMenu menu = new ResearchTableMenu(id, owner, null, BlockPos.ZERO);
        menu.setState(state);
        return menu;
    }
    public ResearchTableBlockEntity table() { return table; }
    public BlockPos position() { return position; }
    public TheorySession session() { return session; }
    public long revision() { return revision; }
    public Set<String> availableAids() { return aids; }
    public boolean canUse() { return canUse; }
    public int inspirationAvailable() { return inspirationAvailable; }
    public int playerExperienceLevel() { return experienceLevel; }
    public Inventory playerInventory() { return playerInventory; }
    public PlayerKnowledge playerKnowledge() { return knowledge; }
    public String status() { return status; }
    public boolean requestPending() { return pending; }
    public void setState(CompoundTag state) {
        revision = state.getLong("Revision");
        canUse = state.getBoolean("CanUse");
        inspirationAvailable = state.getInt("InspirationAvailable");
        experienceLevel = Math.max(0, state.getInt("ExperienceLevel"));
        knowledge = PlayerKnowledge.load(state.getCompound("Knowledge"));
        session = state.contains("Session", Tag.TAG_COMPOUND) ? TheorySession.load(state.getCompound("Session")) : null;
        Set<String> available = new LinkedHashSet<>();
        var strings = state.getList("Aids", Tag.TAG_STRING);
        for (int i = 0; i < strings.size(); i++) if (TheoryAids.keys().contains(strings.getString(i))) available.add(strings.getString(i));
        aids = Set.copyOf(available);
        // An unrelated periodic snapshot is not an acknowledgement of a queued action.
        if (state.contains("Result", Tag.TAG_STRING)) { status = state.getString("Result"); pending = false; }
    }
    public void request(TheoryNetwork.Action action, int cardIndex, Set<String> selectedAids) {
        if (pending || !canUse) return;
        pending = true;
        TheoryNetwork.request(containerId, revision, action, cardIndex, selectedAids);
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (table != null && owner instanceof ServerPlayer player) {
            CompoundTag state = table.clientSnapshot(player, null);
            setState(state);
            if (!state.equals(lastSent)) { lastSent = state.copy(); TheoryNetwork.send(player, containerId, state); }
        }
    }
    @Override public boolean stillValid(Player player) {
        return table != null ? table.stillValid(player) : player != null && player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64
                && player.level().getBlockState(position).is(TheoryModule.TABLE.get());
    }
    private boolean inventoryAccessible() { return table == null ? canUse : table.canUse(owner); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (table != null && !table.canUse(player)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player) || !inventoryAccessible()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        boolean moved;
        if (index < PLAYER_START) moved = moveItemStackTo(stack, PLAYER_START, PLAYER_END, true);
        else if (stack.is(TheoryModule.SCRIBING_TOOLS.get())) moved = moveItemStackTo(stack, 0, 1, false);
        else if (stack.is(Items.PAPER)) moved = moveItemStackTo(stack, 1, 2, false);
        else moved = index < 29 ? moveItemStackTo(stack, 29, PLAYER_END, false) : moveItemStackTo(stack, PLAYER_START, 29, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        broadcastChanges();
        return original;
    }
}
