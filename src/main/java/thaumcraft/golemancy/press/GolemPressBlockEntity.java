package thaumcraft.golemancy.press;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
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
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.research.KnowledgeStore;
import java.util.List;

/** Paid BETA26 manufacture: components at start, then one Machina every fifth tick. */
public final class GolemPressBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, IEssentiaTransport {
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private long golem = -1L;
    private int cost, maxCost, ticks, press;
    private boolean bufferedEssentia, starting, finishing, checking;
    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new SidedInvWrapper(this, Direction.DOWN));

    public GolemPressBlockEntity(BlockPos pos, BlockState state) { super(GolemPressRegistry.PRESS_BE.get(), pos, state); }
    public long golemId() { return golem; }
    public long currentProps() { return golem; }
    public int cost() { return cost; }
    public int maxCost() { return maxCost; }
    public int press() { return press; }
    public float pressAngle() { return press; }
    public boolean busy() { return golem >= 0 || starting || finishing || checking; }
    public Direction facing() { return getBlockState().getValue(GolemPressBlock.FACING); }
    public List<ItemStack> components(long id) { return GolemDesign.parse(id).map(GolemDesign::components).orElse(List.of()); }
    public static ItemStack outputStack(long id) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem"));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putLong("props", id);
        return stack;
    }
    public boolean outputAccepts(long id) {
        ItemStack output = outputStack(id), present = getItem(0);
        return !output.isEmpty() && (present.isEmpty() || present.getCount() < Math.min(getMaxStackSize(), present.getMaxStackSize())
                && ItemStack.isSameItemSameTags(present, output));
    }
    private boolean physicalMenu(ServerPlayer player) {
        return player != null && stillValid(player) && player.containerMenu instanceof GolemPressMenu menu && menu.tile() == this;
    }
    public boolean[] checkCraft(long id, ServerPlayer player) {
        if (!(level instanceof ServerLevel) || !physicalMenu(player) || checking || starting || finishing) return new boolean[0];
        var design = GolemDesign.parse(id).orElse(null);
        if (design == null || !design.canManufacture(KnowledgeStore.get(player))) return new boolean[0];
        checking = true;
        try { return GolemComponentPayment.available(level, worldPosition, player, design.components()); }
        finally { checking = false; }
    }
    public boolean startCraft(long id, ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || busy() || !physicalMenu(player)) return false;
        var design = GolemDesign.parse(id).orElse(null);
        if (design == null || !design.canManufacture(KnowledgeStore.get(player)) || !outputAccepts(id)) return false;
        int required = design.essentiaCost();
        if (required <= 0 || required > 32767) return false;
        // Hold the guard while querying capabilities as even simulated handlers can call back into this machine.
        starting = true;
        try {
            var payment = GolemComponentPayment.reserve(server, worldPosition, player, design.components());
            if (payment == null || !payment.commit(() -> physicalMenu(player) && outputAccepts(id)
                    && golem < 0 && design.canManufacture(KnowledgeStore.get(player)))) return false;
            golem = id; cost = required; maxCost = required; bufferedEssentia = false;
            changed(); playWand(server, .25F);
            return true;
        } finally { starting = false; }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, GolemPressBlockEntity tile) {
        if (tile.isRemoved() || level.getBlockEntity(pos) != tile) return;
        if (level.isClientSide) { tile.clientAnimationTick(); return; }
        tile.ticks = tile.ticks == Integer.MAX_VALUE ? 1 : tile.ticks + 1;
        // The >0 test is intentional: BETA26 does not retry a paid zero-cost blocked output.
        if (tile.ticks % 5 != 0 || tile.cost <= 0 || tile.golem < 0 || tile.starting || tile.finishing) return;
        if (tile.bufferedEssentia || tile.drawEssentia()) {
            tile.bufferedEssentia = false;
            tile.cost--;
            tile.changed();
        }
        if (tile.cost <= 0 && tile.outputAccepts(tile.golem)) {
            tile.finishing = true;
            try {
                ItemStack result = outputStack(tile.golem);
                if (tile.getItem(0).isEmpty()) tile.items.set(0, result); else tile.getItem(0).grow(1);
                // Only actual placement in the output slot retires the already paid design.
                tile.cost = 0; tile.golem = -1L;
                tile.changed(); playWand(level, pos, 1F);
            } finally { tile.finishing = false; }
        }
    }
    private boolean drawEssentia() {
        if (level == null) return false;
        for (Direction face : Direction.values()) {
            if (!isConnectable(face)) continue;
            BlockPos source = worldPosition.relative(face);
            if (!level.hasChunkAt(source) || !(level.getBlockEntity(source) instanceof IEssentiaTransport transport)
                    || !transport.isConnectable(face.getOpposite())) continue;
            // Pinned TileGolemBuilder.drawEssentia returns immediately on its first connected non-output peer.
            if (!transport.canOutputTo(face.getOpposite())) return false;
            if (transport.getSuctionAmount(face.getOpposite()) < getSuctionAmount(face)
                    && transport.takeEssentia(Aspect.MECHANISM, 1, face.getOpposite()) == 1) return true;
        }
        return false;
    }
    /** Root renderer reads pressAngle; native smoke is the modern vent-particle adaptation. */
    public void clientAnimationTick() {
        if (level == null || !level.isClientSide) return;
        // Preserve the pinned >0 check: the all-zero wood design does not animate its press.
        if (press < 90 && cost > 0 && golem > 0) {
            press += 6;
            if (press >= 60) { extinguish(.66F); vents(16); }
        }
        if (press >= 90 && level.random.nextInt(8) == 0) { vents(1); extinguish(.1F); }
        if (press > 0 && (cost <= 0 || golem == -1L)) {
            if (press >= 90) vents(10);
            press -= 3;
        }
    }
    private void extinguish(float volume) {
        level.playLocalSound(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5,
                SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, volume, 1F + level.random.nextFloat() * .1F, false);
    }
    private void vents(int count) {
        for (int index = 0; index < count; index++) level.addParticle(ParticleTypes.SMOKE,
                worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5,
                level.random.nextGaussian() * .1, 0, level.random.nextGaussian() * .1);
    }
    private static void playWand(Level level, BlockPos pos, float volume) {
        var sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "wand"));
        if (sound == null) sound = SoundEvents.ENCHANTMENT_TABLE_USE;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, 1);
    }
    private void playWand(Level level, float volume) { playWand(level, worldPosition, volume); }
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public boolean isConnectable(Direction face) { return face != null && (face.getAxis().isHorizontal() || face == Direction.DOWN); }
    @Override public boolean canInputFrom(Direction face) { return isConnectable(face); }
    @Override public boolean canOutputTo(Direction face) { return false; }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public Aspect getSuctionType(Direction face) { return Aspect.MECHANISM; }
    @Override public int getSuctionAmount(Direction face) { return cost > 0 && golem >= 0 ? 128 : 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getEssentiaType(Direction face) { return null; }
    @Override public int getEssentiaAmount(Direction face) { return 0; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction face) { return 0; }
    @Override public int addEssentia(Aspect aspect, int amount, Direction face) {
        if (level != null && level.isClientSide || isRemoved() || !canInputFrom(face) || amount <= 0 || aspect != Aspect.MECHANISM
                || bufferedEssentia || cost <= 0 || golem < 0 || starting || finishing) return 0;
        bufferedEssentia = true;
        return 1;
    }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? items.get(0) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        var removed = ContainerHelper.removeItem(items, slot, amount); if (!removed.isEmpty()) changed(); return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return slot == 0 ? ContainerHelper.takeItem(items, slot) : ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        if (getItem(0).getCount() > Math.min(getMaxStackSize(), getItem(0).getMaxStackSize())) getItem(0).setCount(Math.min(getMaxStackSize(), getItem(0).getMaxStackSize()));
        changed();
    }
    @Override public void clearContent() { items.set(0, ItemStack.EMPTY); changed(); }
    @Override public boolean stillValid(Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && !isRemoved() && level != null && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this && player.level() == level && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
    // GUI SlotOutput refuses insertion; inherited BETA26 inventory APIs separately accept any original placer item.
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && !stack.isEmpty() && stack.is(outputStack(0).getItem());
    }
    @Override public int[] getSlotsForFace(Direction face) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot == 0; }
    @Override public Component getDisplayName() { return Component.translatable("block.thaumcraft.golem_builder"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new GolemPressMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items);
        tag.putLong("golem", golem); tag.putInt("cost", cost); tag.putInt("mcost", maxCost);
        // TileGolemBuilder deliberately does not save bufferedEssentia, ticks or press.
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(1, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        ItemStack output = getItem(0);
        if (!output.isEmpty() && output.getCount() > Math.min(64, output.getMaxStackSize())) output.setCount(Math.min(64, output.getMaxStackSize()));
        golem = tag.contains("golem", Tag.TAG_LONG) ? tag.getLong("golem") : -1L;
        cost = Math.max(0, tag.getInt("cost")); maxCost = Math.max(0, Math.min(32767, tag.getInt("mcost")));
        var design = GolemDesign.parse(golem).orElse(null);
        if (golem >= 0 && design != null && design.rank() == 0) {
            int bound = design.essentiaCost();
            if (cost > bound || maxCost != bound) { golem = -1L; cost = 0; maxCost = 0; }
        } else if (golem != -1L || cost != 0) { golem = -1L; cost = 0; maxCost = 0; }
        bufferedEssentia = false; ticks = 0; starting = false; finishing = false; checking = false;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1, worldPosition.getX() + 2, worldPosition.getY() + 2, worldPosition.getZ() + 2); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved() && capability == ForgeCapabilities.ITEM_HANDLER) return itemHandler.cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemHandler.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemHandler = LazyOptional.of(() -> new SidedInvWrapper(this, Direction.DOWN)); }
}
