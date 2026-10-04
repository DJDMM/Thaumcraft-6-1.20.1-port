package thaumcraft.golemancy.press;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.research.KnowledgeStore;
import java.util.List;

/** Original 208x224 press container: one output-only slot and ordinary player storage. */
public final class GolemPressMenu extends AbstractContainerMenu {
    private final Container output;
    private final Inventory inventory;
    private final BlockPos position;
    private int cost, maxCost, revision;
    private long checkedDesignId = -1L;
    private boolean[] availability = new boolean[0];

    public GolemPressMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(1), buffer.readBlockPos());
    }
    public static GolemPressMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new GolemPressMenu(id, inventory, buffer);
    }
    public GolemPressMenu(int id, Inventory inventory, GolemPressBlockEntity tile) {
        this(id, inventory, tile, tile.getBlockPos());
    }
    private GolemPressMenu(int id, Inventory inventory, Container output, BlockPos position) {
        super(GolemPressRegistry.PRESS_MENU.get(), id);
        this.output = output;
        this.inventory = inventory;
        this.position = position.immutable();
        addSlot(new Slot(output, 0, 160, 104) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, 9 + row * 9 + column, 24 + column * 18, 142 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 24 + column * 18, 200));
        addDataSlot(new DataSlot() {
            @Override public int get() { return tile() == null ? cost : tile().cost(); }
            @Override public void set(int value) { cost = Math.max(0, value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return tile() == null ? maxCost : tile().maxCost(); }
            @Override public void set(int value) { maxCost = Math.max(0, value); }
        });
    }
    public BlockPos position() { return position; }
    public GolemPressBlockEntity tile() { return output instanceof GolemPressBlockEntity tile ? tile : null; }
    public int cost() { return tile() == null ? cost : tile().cost(); }
    public int maxCost() { return tile() == null ? maxCost : tile().maxCost(); }
    public long checkedDesignId() { return checkedDesignId; }
    public long previewDesignId() { return checkedDesignId; }
    public int revision() { return revision; }
    public boolean[] availability() { return availability.clone(); }
    public List<Boolean> owns() {
        var flags = new java.util.ArrayList<Boolean>();
        for (boolean flag : availability) flags.add(flag);
        return List.copyOf(flags);
    }
    public void acceptSnapshot(int revision, long design, List<Boolean> flags) {
        if (revision < this.revision || flags == null || flags.size() > 32) return;
        this.revision = revision;
        checkedDesignId = design;
        availability = new boolean[flags.size()];
        for (int index = 0; index < flags.size(); index++) availability[index] = Boolean.TRUE.equals(flags.get(index));
    }
    public void acceptCheck(long id, boolean[] flags) {
        checkedDesignId = id;
        availability = flags == null ? new boolean[0] : flags.clone();
    }
    /** Called only after the scoped C2S handler checks container id and block position. */
    public boolean startDesign(long id) {
        if (!(inventory.player instanceof ServerPlayer player) || player.containerMenu != this
                || !stillValid(player) || tile() == null || !tile().startCraft(id, player)) return false;
        revision++;
        checkedDesignId = id;
        availability = tile().checkCraft(id, player);
        return true;
    }
    public boolean startDesign(ServerPlayer player, int expectedRevision, long id) {
        return inventory.player == player && expectedRevision == revision && startDesign(id);
    }
    public boolean[] checkDesign(long id) {
        if (!(inventory.player instanceof ServerPlayer player) || player.containerMenu != this
                || !stillValid(player) || tile() == null) return new boolean[0];
        return tile().checkCraft(id, player);
    }
    /** Preview changes only this menu's detached display; machine/inventories/knowledge stay untouched. */
    public boolean preview(ServerPlayer player, long id) {
        if (inventory.player != player || player.containerMenu != this || !stillValid(player) || tile() == null) return false;
        var design = GolemDesign.parse(id).orElse(null);
        if (design == null || !design.canManufacture(KnowledgeStore.get(player))) return false;
        checkedDesignId = id;
        availability = tile().checkCraft(id, player);
        return availability.length == design.components().size();
    }
    public boolean preview(ServerPlayer player, int expectedRevision, long id) {
        return expectedRevision == revision && preview(player, id);
    }
    @Override public boolean stillValid(Player player) {
        return output instanceof GolemPressBlockEntity tile ? tile.stillValid(player)
                : !player.isSpectator() && player.isAlive() && player.level().hasChunkAt(position)
                && player.level().getBlockState(position).getBlock() instanceof GolemPressBlock
                && player.distanceToSqr(position.getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index != 0 || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(0);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (!moveItemStackTo(source, 1, slots.size(), true)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
