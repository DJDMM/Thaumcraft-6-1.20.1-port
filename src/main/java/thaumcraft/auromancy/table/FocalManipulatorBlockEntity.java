package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** The paid spell and table inventory belong to the server, never to an open screen. */
public final class FocalManipulatorBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private ItemStack item = ItemStack.EMPTY;
    private FocusGraph graph = new FocusGraph(java.util.List.of());
    private String focusName = "";
    private long revision;
    private FocusPlan paidPlan;
    private ItemStack paidInput = ItemStack.EMPTY;
    private float remainingVis;
    private int ticks;
    private LazyOptional<IItemHandler> items;

    public FocalManipulatorBlockEntity(BlockPos pos, BlockState state) {
        super(FocalManipulatorModule.TABLE.get(), pos, state);
        createHandler();
    }
    private void createHandler() {
        items = LazyOptional.of(() -> new InvWrapper(this) {
            @Override public ItemStack getStackInSlot(int slot) { return super.getStackInSlot(slot).copy(); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return mutable() ? super.insertItem(slot, stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return mutable() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
        });
    }
    private boolean mutable() { return level == null || level instanceof ServerLevel server && server.getServer().isSameThread(); }
    public long revision() { return revision; }
    public FocusGraph graph() { return graph; }
    public String focusName() { return focusName; }
    public boolean crafting() { return paidPlan != null && remainingVis > 0; }
    public float remainingVis() { return remainingVis; }
    public Map<String,Integer> orbitCrystals() { return paidPlan == null ? Map.of() : paidPlan.crystals(); }
    public boolean canUse(Player player) { return player != null && player.level() == level && player.isAlive() && !player.isSpectator()
            && (!(level instanceof ServerLevel server) || server.getServer().isSameThread()) && stillValid(player); }
    private FocalManipulatorResult guard(ServerPlayer player, long expected) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !canUse(player)) return FocalManipulatorResult.LOCKED;
        if (expected != revision) return FocalManipulatorResult.STALE;
        if (revision == Long.MAX_VALUE) return FocalManipulatorResult.OVERFLOW;
        return crafting() ? FocalManipulatorResult.BUSY : FocalManipulatorResult.ACCEPTED;
    }
    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private FocalManipulatorResult commit() { if (revision < Long.MAX_VALUE) revision++; changed(); return FocalManipulatorResult.ACCEPTED; }

    public FocalManipulatorResult edit(ServerPlayer player, long expected, CompoundTag data, String name) {
        FocalManipulatorResult result = guard(player, expected);
        if (result != FocalManipulatorResult.ACCEPTED) return result;
        if (!FocusStacks.isFocus(item)) return FocalManipulatorResult.MISSING_FOCUS;
        FocusGraph edited;
        try { edited = FocusGraph.read(data); }
        catch (RuntimeException malformed) { return FocalManipulatorResult.INVALID; }
        result = validateEditor(edited, player);
        if (result != FocalManipulatorResult.ACCEPTED) return result;
        // Intermediate editor graphs may have empty sockets, but an edited complete graph must
        // still be validated afresh by compile at START. They are never executable merely by saving.
        graph = edited; focusName = safeName(name);
        return commit();
    }
    private static FocalManipulatorResult validateEditor(FocusGraph graph, ServerPlayer player) {
        if (graph.nodes().isEmpty()) return FocalManipulatorResult.ACCEPTED;
        Map<Integer,FocusGraph.Node> nodes = new LinkedHashMap<>();
        for (var node : graph.nodes()) {
            if (node.id() < 0 || node.id() >= FocusGraph.MAX_NODES || node.parent() < -1 || node.parent() >= FocusGraph.MAX_NODES
                    || nodes.put(node.id(),node) != null) return FocalManipulatorResult.INVALID;
            var definition = FocusNodeRegistry.get(node.key());
            if (definition == null) {
                if (!node.key().isEmpty() || !node.children().isEmpty() || !node.settings().isEmpty() || node.id() == 0) return FocalManipulatorResult.INVALID;
            } else {
                if (!definition.runtimeSupported()) return FocalManipulatorResult.UNSUPPORTED;
                if (node.id() != 0 && !KnowledgeStore.get(player).isResearchCompleteStrict(definition.research())) return FocalManipulatorResult.MISSING_RESEARCH;
                for (var value : node.settings().entrySet()) {
                    var setting = definition.settings().get(value.getKey());
                    if (setting == null || !setting.accepts(value.getValue())) return FocalManipulatorResult.INVALID;
                    if (setting.research() != null && value.getValue() != setting.defaultValue()
                            && !KnowledgeStore.get(player).isResearchCompleteStrict(setting.research())) return FocalManipulatorResult.MISSING_RESEARCH;
                }
            }
        }
        var root = nodes.get(0);
        if (root == null || root.parent() != -1 || !root.key().equals(FocusNodeRegistry.ROOT)) return FocalManipulatorResult.INVALID;
        for (var node : graph.nodes()) {
            if (node.id() != 0 && (node.parent() < 0 || node.key().equals(FocusNodeRegistry.ROOT))) return FocalManipulatorResult.INVALID;
            java.util.Set<Integer> children = new java.util.HashSet<>();
            for (int child : node.children()) {
                var target = nodes.get(child);
                if (!children.add(child) || target == null || target.parent() != node.id()) return FocalManipulatorResult.INVALID;
            }
            if (node.id() != 0) {
                var parent = nodes.get(node.parent());
                if (parent == null || !parent.children().contains(node.id())) return FocalManipulatorResult.INVALID;
            }
        }
        java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>(); pending.add(0);
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        while (!pending.isEmpty()) {
            int id = pending.removeFirst();
            if (!visited.add(id)) return FocalManipulatorResult.INVALID;
            pending.addAll(nodes.get(id).children());
        }
        return visited.size() == nodes.size() ? FocalManipulatorResult.ACCEPTED : FocalManipulatorResult.INVALID;
    }
    public FocalManipulatorResult start(ServerPlayer player, long expected) {
        FocalManipulatorResult result = guard(player, expected);
        if (result != FocalManipulatorResult.ACCEPTED) return result;
        if (!FocusStacks.isFocus(item)) return FocalManipulatorResult.MISSING_FOCUS;
        var compiled = FocusCompiler.compile(graph, item.copy(), KnowledgeStore.get(player)::isResearchCompleteStrict);
        if (!compiled.success()) return compilationFailure(compiled.error());
        FocusPlan plan = compiled.plan();
        if (!player.getAbilities().instabuild && player.experienceLevel < plan.xpLevels()) return FocalManipulatorResult.MISSING_XP;
        int[] debit = crystalDebit(player.getInventory(), plan.crystals());
        if (debit == null) return FocalManipulatorResult.MISSING_CRYSTALS;
        // Explicit correction of the old XP-before-crystals bug: preflight every resource, then
        // one server callback debits main inventory and XP. Creative still pays crystals as TC6 did.
        for (int slot = 0; slot < debit.length; slot++) if (debit[slot] != 0) player.getInventory().items.get(slot).shrink(debit[slot]);
        player.getInventory().setChanged();
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-plan.xpLevels());
        paidPlan = plan; paidInput = item.copy(); remainingVis = plan.craftVis();
        sound("craftstart", 1);
        return commit();
    }
    private static FocalManipulatorResult compilationFailure(String error) {
        if (error != null && error.contains("research")) return FocalManipulatorResult.MISSING_RESEARCH;
        if (error != null && error.contains("unsupported")) return FocalManipulatorResult.UNSUPPORTED;
        return FocalManipulatorResult.INVALID;
    }
    public static int[] crystalDebit(Inventory inventory, Map<String,Integer> cost) {
        int[] debit = new int[inventory.items.size()];
        for (var requirement : cost.entrySet()) {
            Aspect aspect = Aspect.getAspect(requirement.getKey());
            if (aspect == null || requirement.getValue() == null || requirement.getValue() <= 0) return null;
            ItemStack required = AspectCrystalItem.create(aspect);
            if (required.isEmpty()) return null;
            int needed = requirement.getValue();
            for (int slot = 0; slot < debit.length && needed > 0; slot++) {
                ItemStack available = inventory.items.get(slot);
                if (!matchesCrystal(available, required, aspect)) continue;
                int take = Math.min(needed, available.getCount() - debit[slot]);
                if (take > 0) { debit[slot] += take; needed -= take; }
            }
            if (needed > 0) return null;
        }
        return debit;
    }
    private static boolean matchesCrystal(ItemStack actual, ItemStack required, Aspect aspect) {
        if (actual.isEmpty()) return false;
        if (!(actual.getItem() instanceof AspectCrystalItem)) return AspectCrystalItem.crystalAspect(actual) == aspect;
        if (!actual.is(required.getItem()) || actual.getTag() == null) return false;
        for (String key : required.getTag().getAllKeys())
            if (!required.getTag().get(key).equals(actual.getTag().get(key))) return false;
        return true;
    }
    private void cancel() { paidPlan = null; paidInput = ItemStack.EMPTY; remainingVis = 0; }
    public float spendAura(float requested) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || requested <= 0 || !Float.isFinite(requested)) return 0;
        if (level.getBlockState(worldPosition.above()).is(CatalogBlocks.block("arcane_workbench_charger"))) {
            float remaining = requested, share = requested / 9;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                remaining -= AuraManager.drainVis(server, worldPosition.offset(x * 16, 0, z * 16), Math.min(share, remaining), false);
                if (remaining <= 0) return requested;
            }
            return requested - remaining;
        }
        return AuraManager.drainVis(server, worldPosition, requested, false);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, FocalManipulatorBlockEntity table) {
        if (level.isClientSide) {
            if (table.crafting() && level.random.nextInt(3) == 0)
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + .5, pos.getY() + 1.4, pos.getZ() + .5,
                        (level.random.nextFloat() - .5) * .3, .05, (level.random.nextFloat() - .5) * .3);
            return;
        }
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        table.ticks = (table.ticks + 1) % 20;
        if (table.crafting()) table.setChanged(); // Persist the current cadence without a packet each tick.
        if (table.ticks != 0 || !table.crafting()) return;
        if (!FocusStacks.isFocus(table.item) || !ItemStack.matches(table.item, table.paidInput)) {
            table.cancel(); table.sound("wandfail", .33F); table.commit(); return;
        }
        float debit = table.spendAura(Math.min(20, table.remainingVis));
        if (debit > 0) {
            table.remainingVis = Math.max(0, table.remainingVis - debit);
            server.blockEvent(pos, state.getBlock(), 5, 1);
            if (table.remainingVis <= 0) {
                // apply returns a new stack and keeps custom input NBT. Only this callback writes it.
                table.item = FocusStacks.apply(table.item, table.paidPlan, table.focusName);
                table.cancel(); table.graph = new FocusGraph(java.util.List.of());
                table.sound("wand", 1); table.commit();
            } else table.changed();
        }
    }
    private void sound(String key, float volume) {
        var event = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", key));
        if (level != null && event != null) level.playSound(null, worldPosition, event, SoundSource.BLOCKS, volume, 1);
    }
    @Override public boolean triggerEvent(int id, int value) {
        if (id == 5) {
            if (level != null && level.isClientSide) for (int i = 0; i < 8; i++)
                level.addParticle(ParticleTypes.ENCHANT, worldPosition.getX() + .5, worldPosition.getY() + 1.05, worldPosition.getZ() + .5,
                        (level.random.nextFloat() - .5) * 1.5, level.random.nextFloat(), (level.random.nextFloat() - .5) * 1.5);
            return true;
        }
        return super.triggerEvent(id, value);
    }
    public CompoundTag clientSnapshot(ServerPlayer player, FocalManipulatorResult result) {
        CompoundTag state = getUpdateTag();
        state.putBoolean("CanUse", canUse(player));
        state.put("Knowledge", KnowledgeStore.get(player).save());
        state.putInt("ExperienceLevel", Math.max(0, player.experienceLevel));
        state.putBoolean("Creative", player.getAbilities().instabuild);
        if (result != null) state.putString("Result", result.name());
        return state;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Focus", item.save(new CompoundTag())); tag.put("Graph", graph.save());
        tag.putString("FocusName", focusName); tag.putLong("Revision", revision); tag.putInt("Ticks", ticks);
        if (paidPlan != null) {
            tag.put("PaidGraph", paidPlan.graph().save()); tag.put("PaidInput", paidInput.save(new CompoundTag()));
            tag.putFloat("RemainingVis", remainingVis);
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag savedFocus = tag.getCompound("Focus");
        item = ItemStack.of(savedFocus.copy());
        if (!FocusStacks.isFocus(item) || item.getCount() != 1 || !savedFocus.contains("Count", Tag.TAG_BYTE)) item = ItemStack.EMPTY;
        graph = readGraph(tag.getCompound("Graph")); focusName = safeName(tag.getString("FocusName"));
        revision = Math.max(0, tag.getLong("Revision")); ticks = Math.floorMod(tag.getInt("Ticks"), 20);
        cancel();
        if (tag.contains("PaidGraph", Tag.TAG_COMPOUND)) {
            CompoundTag savedInput = tag.getCompound("PaidInput");
            var input = ItemStack.of(savedInput.copy());
            var compiled = FocusCompiler.compile(readGraph(tag.getCompound("PaidGraph")), input, ignored -> true);
            float pending = tag.getFloat("RemainingVis");
            if (compiled.success() && savedInput.contains("Count", Tag.TAG_BYTE) && tag.contains("RemainingVis", Tag.TAG_FLOAT) && ItemStack.matches(item, input)
                    && Float.isFinite(pending) && pending > 0 && pending <= compiled.plan().craftVis()) {
                paidPlan = compiled.plan(); paidInput = input.copy(); remainingVis = pending;
            }
        }
    }
    private static FocusGraph readGraph(CompoundTag tag) {
        try { return FocusGraph.read(tag); } catch (RuntimeException ignored) { return new FocusGraph(java.util.List.of()); }
    }
    private static String safeName(String raw) {
        if (raw == null) return "";
        return FocusStacks.cleanName(raw);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        // Cadence is persisted server state, not a client animation value. Omitting it keeps
        // the menu from resending its complete knowledge snapshot every idle server tick.
        tag.remove("Ticks"); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), worldPosition.getX()+1, worldPosition.getY()+2, worldPosition.getZ()+1); }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return item.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? item.copy() : ItemStack.EMPTY; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && FocusStacks.isFocus(stack); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0 || !mutable() || !stack.isEmpty() && !FocusStacks.isFocus(stack)) return;
        ItemStack next = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(item, next)) return;
        item = next; cancel(); graph = new FocusGraph(java.util.List.of()); focusName = next.isEmpty() ? "" : next.getHoverName().getString();
        commit();
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0 || item.isEmpty() || !mutable()) return ItemStack.EMPTY;
        ItemStack out = item.copy(); setItem(0, ItemStack.EMPTY); return out;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
    @Override public void clearContent() { setItem(0, ItemStack.EMPTY); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public int[] getSlotsForFace(Direction direction) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot == 0; }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER && !isRemoved() ? items.cast() : super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); createHandler(); }
    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.wand_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new FocalManipulatorMenu(id, inventory, this); }
}
