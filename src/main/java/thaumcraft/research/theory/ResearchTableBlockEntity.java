package thaumcraft.research.theory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.ResearchCategories;
import thaumcraft.research.ResearchNetwork;

import java.util.Map;
import java.util.Set;

/** Server-owned table transactions. Revisions and owner survive unloading the chunk. */
public final class ResearchTableBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INK = 0, PAPER = 1;
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private TheorySession session;
    private long revision;

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) { super(TheoryModule.TABLE_TILE.get(), pos, state); }
    public TheorySession session() { return session; }
    public long revision() { return revision; }
    public boolean canUse(Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && stillValid(player)
                && (session == null || session.owner().equals(player.getUUID()));
    }
    private TheoryResult guard(ServerPlayer player, long expectedRevision) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !canUse(player)) return TheoryResult.LOCKED;
        if (expectedRevision != revision) return TheoryResult.STALE;
        return revision == Long.MAX_VALUE ? TheoryResult.OVERFLOW : TheoryResult.ACCEPTED;
    }
    private TheoryResult commit() { revision++; setChanged(); return TheoryResult.ACCEPTED; }
    @Override public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public boolean hasInk() {
        ItemStack tools = items.get(INK);
        return tools.is(TheoryModule.SCRIBING_TOOLS.get()) && tools.getDamageValue() < tools.getMaxDamage();
    }
    public boolean hasPaper() { return items.get(PAPER).is(Items.PAPER) && !items.get(PAPER).isEmpty(); }

    public TheoryResult start(ServerPlayer player, long expectedRevision, Set<String> aids) {
        TheoryResult result = guard(player, expectedRevision);
        if (result != TheoryResult.ACCEPTED) return result;
        if (session != null || aids == null || aids.size() > TheoryAids.keys().size()
                || aids.size() >= TheorySession.availableInspiration(KnowledgeStore.get(player))
                || !checkSurroundingAids().containsAll(aids)) return TheoryResult.INVALID;
        // TC6 enables its Create button only when paper and usable tools are present.
        if (!hasPaper() || !hasInk()) return TheoryResult.MISSING_RESOURCES;
        session = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), aids, player.getRandom());
        return commit();
    }
    public TheoryResult draw(ServerPlayer player, long expectedRevision, boolean bonus) {
        TheoryResult result = guard(player, expectedRevision);
        if (result != TheoryResult.ACCEPTED) return result;
        if (session == null) return TheoryResult.NO_SESSION;
        if (session.complete() || !session.choices().isEmpty()) return TheoryResult.INVALID;
        if (!hasPaper()) return TheoryResult.MISSING_RESOURCES;
        if (!session.draw(player, bonus)) return TheoryResult.INVALID;
        items.get(PAPER).shrink(1);
        return commit();
    }
    public TheoryResult select(ServerPlayer player, long expectedRevision, int index) {
        TheoryResult result = guard(player, expectedRevision);
        if (result != TheoryResult.ACCEPTED) return result;
        if (session == null) return TheoryResult.NO_SESSION;
        // Check before activation: Analyze can spend observation knowledge.
        if (!hasInk()) return TheoryResult.MISSING_RESOURCES;
        if (index < 0 || index >= session.choices().size()) return TheoryResult.INVALID;
        TheoryCard card = session.choices().get(index);
        if (!session.select(player, index)) return TheoryResult.INVALID;
        ItemStack tools = items.get(INK);
        // Exhausted tools remain in the slot and can be refilled; never hurtAndBreak.
        tools.setDamageValue(Math.min(tools.getMaxDamage(), tools.getDamageValue() + 1 + card.tableInkExtra()));
        // Scripting's BETA26 activation ignores a failed extra-paper callback.
        // An exhausted ink set remains in the slot; its second callback cannot exceed 100 damage.
        if (card.tablePaperExtra() > 0 && hasPaper()) items.get(PAPER).shrink(card.tablePaperExtra());
        return commit();
    }
    public TheoryResult finish(ServerPlayer player, long expectedRevision) {
        TheoryResult result = guard(player, expectedRevision);
        if (result != TheoryResult.ACCEPTED) return result;
        if (session == null) return TheoryResult.NO_SESSION;
        if (!session.complete()) return TheoryResult.INVALID;
        Map<String, Integer> rewards = session.rewards();
        KnowledgeStore store = KnowledgeStore.of(player.serverLevel());
        var knowledge = store.get(player.getUUID());
        for (var reward : rewards.entrySet()) {
            if (!ResearchCategories.contains(reward.getKey()) || reward.getValue() < 0) return TheoryResult.INVALID;
            if ((long) knowledge.rawKnowledge(KnowledgeType.THEORY, reward.getKey()) + reward.getValue() > Integer.MAX_VALUE) return TheoryResult.OVERFLOW;
        }
        // All credits have been checked on the server thread, before changing any category.
        rewards.forEach((category, amount) -> { if (amount > 0) store.addKnowledge(player.getUUID(), KnowledgeType.THEORY, category, amount); });
        session = null;
        result = commit();
        ResearchNetwork.sync(player);
        return result;
    }
    public TheoryResult scrap(ServerPlayer player, long expectedRevision) {
        TheoryResult result = guard(player, expectedRevision);
        if (result != TheoryResult.ACCEPTED) return result;
        if (session == null) return TheoryResult.NO_SESSION;
        if (session.complete()) return TheoryResult.INVALID;
        session = null;
        return commit();
    }

    /** Original aid search: offsets -4..4 horizontally and -1..1 vertically; no chunk loads. */
    public Set<String> checkSurroundingAids() {
        return level == null ? Set.of() : TheoryAids.find(level, worldPosition);
    }
    public CompoundTag clientSnapshot(ServerPlayer player, TheoryResult result) {
        CompoundTag state = new CompoundTag();
        state.putLong("Revision", revision);
        state.putBoolean("CanUse", canUse(player));
        var knowledge = KnowledgeStore.get(player);
        state.putInt("InspirationAvailable", TheorySession.availableInspiration(knowledge));
        state.putInt("ExperienceLevel", player.experienceLevel);
        CompoundTag savedKnowledge = knowledge.save();
        savedKnowledge.remove("Scans"); savedKnowledge.remove("CreditedScans");
        state.put("Knowledge", savedKnowledge);
        ListTag aids = new ListTag();
        checkSurroundingAids().forEach(aid -> aids.add(StringTag.valueOf(aid)));
        state.put("Aids", aids);
        if (session != null) state.put("Session", session.save());
        if (result != null) state.putString("Result", result.name());
        return state;
    }

    @Override public Component getDisplayName() { return Component.translatable("container.thaumcraft.research_table"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ResearchTableMenu(id, inventory, this); }
    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), slot == INK ? 1 : stack.getMaxStackSize()));
        setChanged();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INK ? stack.is(TheoryModule.SCRIBING_TOOLS.get()) : slot == PAPER && stack.is(Items.PAPER);
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return player != null && !isRemoved() && player.level() == level && Container.stillValidBlockEntity(this, player);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putLong("TheoryRevision", revision);
        if (session != null) tag.put("Session", session.save());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear(); ContainerHelper.loadAllItems(tag, items);
        for (int i = 0; i < 2; i++) if (!items.get(i).isEmpty()) items.get(i).setCount(Math.min(items.get(i).getCount(), i == INK ? 1 : items.get(i).getMaxStackSize()));
        revision = tag.contains("TheoryRevision", Tag.TAG_LONG) ? Math.max(0, tag.getLong("TheoryRevision")) : 0;
        session = tag.contains("Session", Tag.TAG_COMPOUND) ? TheorySession.load(tag.getCompound("Session")) : null;
    }
}
