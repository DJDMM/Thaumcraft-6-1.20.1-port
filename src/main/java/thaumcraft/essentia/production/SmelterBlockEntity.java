package thaumcraft.essentia.production;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.api.aspects.*;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;

/** Fuel burns independently of storage extraction, as in TileSmelter BETA26. */
public final class SmelterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int CAPACITY = 256;
    private static final int[] NO_SLOTS = {}, INPUT_SLOTS = {0}, FUEL_SLOTS = {1};
    // EnumFacing.HORIZONTALS was indexed by the original horizontal indices.
    // Keep SOUTH/WEST/NORTH/EAST for pump priority and per-side vent RNG ordering.
    private static final Direction[] SIDES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private AspectList stored = new AspectList();
    private int burnTime, currentBurnTime, cookTime, smeltTime = 100, ticks, bellows = -1;
    private boolean speedBoost;
    private LazyOptional<IItemHandlerModifiable>[] itemHandlers = SidedInvWrapper.create(this, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private LazyOptional<IItemHandler> unsidedItems = LazyOptional.of(() -> new InvWrapper(this));
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) { case 0 -> cookTime; case 1 -> burnTime; case 2 -> currentBurnTime; case 3 -> totalEssentia(); case 4 -> smeltTime; default -> 0; }; }
        @Override public void set(int index, int value) { switch (index) { case 0 -> cookTime = value; case 1 -> burnTime = value; case 2 -> currentBurnTime = value; case 4 -> smeltTime = value; default -> {} } }
        @Override public int getCount() { return 5; }
    };
    public SmelterBlockEntity(BlockPos pos, BlockState state) { super(EssentiaProductionModule.SMELTER.get(), pos, state); }
    public int tier() { return getBlockState().getBlock() instanceof SmelterBlock smelter ? smelter.tier() : 0; }
    public int totalEssentia() { return stored.visSize(); }
    public AspectList storedAspects() { return stored.copy(); }
    public int burnTime() { return burnTime; }
    public int cookTime() { return cookTime; }
    public int smeltTime() { return smeltTime; }
    public int currentBurnTime() { return currentBurnTime; }
    public boolean speedBoost() { return speedBoost; }
    public int distillationInterval() { return SmelterRules.interval(tier(), speedBoost); }
    public ContainerData menuData() { return data; }
    /** Same bounded loader used for saved contents and deterministic fixtures. */
    public void setStoredAspects(AspectList aspects) {
        if (level != null && level.isClientSide) return;
        stored = bounded(aspects); changed();
    }
    private static AspectList bounded(AspectList input) {
        AspectList output = new AspectList(); int room = CAPACITY;
        for (Aspect aspect : input.getAspects()) {
            if (aspect == null) continue;
            int amount = Math.max(0, Math.min(room, input.getAmount(aspect)));
            if (amount > 0) { output.add(aspect, amount); room -= amount; }
            if (room == 0) break;
        }
        return output;
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public static int fuelTicks(ItemStack stack) { return stack.isEmpty() ? 0 : ForgeHooks.getBurnTime(stack, RecipeType.SMELTING); }
    public boolean canSmelt() {
        if (getItem(0).isEmpty()) return false;
        AspectList input = AspectRegistry.getAspects(getItem(0));
        long total = 0;
        for (Aspect type : input.getAspects()) {
            if (type == null || input.getAmount(type) <= 0) return false;
            total += input.getAmount(type);
        }
        if (total <= 0 || total > CAPACITY - totalEssentia()) return false;
        if (bellows < 0) checkNeighbours();
        smeltTime = SmelterRules.cookTicks((int)total, bellows);
        return true;
    }
    public void checkNeighbours() {
        bellows = 0;
        if (level == null) return;
        Direction front = getBlockState().getValue(SmelterBlock.FACING);
        for (Direction side : SIDES) {
            BlockPos adjacent = worldPosition.relative(side);
            if (side == front || !level.hasChunkAt(adjacent)) continue;
            BlockState state = level.getBlockState(adjacent);
            if (state.getBlock() instanceof SmelterBellowsBlock && state.getValue(SmelterBellowsBlock.FACING) == side.getOpposite()
                    && state.getValue(SmelterBellowsBlock.ENABLED)) bellows++;
        }
    }
    public boolean takeFromContainer(Aspect type, int units) {
        if (level != null && level.isClientSide || type == null || units <= 0 || stored.getAmount(type) < units) return false;
        stored.remove(type, units); changed(); return true;
    }
    public boolean smeltItem() {
        if (!(level instanceof ServerLevel server) || !canSmelt()) return false;
        SmelterRules.LossResult loss = SmelterRules.rollLoss(AspectRegistry.getAspects(getItem(0)), tier(), level.random);
        stored.add(loss.retained());
        int flux = 0;
        Direction front = getBlockState().getValue(SmelterBlock.FACING);
        for (int i = 0; i < loss.lost(); i++) {
            boolean captured = false;
            for (Direction face : SIDES) {
                BlockPos adjacent = worldPosition.relative(face);
                if (face == front || !level.hasChunkAt(adjacent)) continue;
                BlockState state = level.getBlockState(adjacent);
                if (state.getBlock() instanceof SmelterAttachmentBlock attachment && attachment.isVent()
                        && state.getValue(SmelterAttachmentBlock.FACING) == face.getOpposite() && level.random.nextFloat() < .333D) {
                    level.blockEvent(worldPosition, getBlockState().getBlock(), 1, face.getOpposite().get3DDataValue());
                    captured = true; break;
                }
            }
            if (!captured) flux++;
        }
        AuraManager.addFlux(server, worldPosition, flux);
        getItem(0).shrink(1);
        if (getItem(0).isEmpty()) items.set(0, ItemStack.EMPTY);
        changed(); return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity smelter) {
        if (level.isClientSide) return;
        boolean wasBurning = smelter.burnTime > 0;
        smelter.ticks++;
        if (smelter.burnTime > 0) smelter.burnTime--;
        if (smelter.bellows < 0) smelter.checkNeighbours();
        // Stored essentia can be distilled after all fuel has expired.
        if (smelter.ticks % smelter.distillationInterval() == 0 && smelter.totalEssentia() > 0) {
            smelter.distillColumn(pos);
            Direction front = state.getValue(SmelterBlock.FACING);
            for (Direction face : SIDES) {
                BlockPos adjacent = pos.relative(face);
                if (face == front || !level.hasChunkAt(adjacent)) continue;
                BlockState attachment = level.getBlockState(adjacent);
                if (attachment.getBlock() instanceof SmelterAttachmentBlock pump && !pump.isVent()
                        && attachment.getValue(SmelterAttachmentBlock.FACING) == face.getOpposite()) smelter.distillColumn(adjacent);
            }
        }
        boolean valid = smelter.canSmelt();
        if (smelter.burnTime == 0 && valid) {
            int burn = fuelTicks(smelter.getItem(1));
            smelter.burnTime = burn; smelter.currentBurnTime = burn;
            if (burn > 0) {
                ItemStack fuel = smelter.getItem(1);
                smelter.speedBoost = fuel.is(AlchemyModule.ALUMENTUM.get());
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty()) smelter.items.set(1, remainder);
                smelter.changed();
            }
        }
        boolean burning = smelter.burnTime > 0;
        if (state.getValue(SmelterBlock.ENABLED) != burning) level.setBlock(pos, state.setValue(SmelterBlock.ENABLED, burning), 3);
        if (burning && valid) {
            if (++smelter.cookTime >= smelter.smeltTime) { smelter.cookTime = 0; smelter.smeltItem(); }
        } else smelter.cookTime = 0;
        // Persist counters without sending an inventory/block packet every tick.
        if (wasBurning || burning) smelter.setChanged();
        if (wasBurning != burning || smelter.ticks % 20 == 0 && burning) smelter.changed();
    }
    private void distillColumn(BlockPos base) {
        for (Aspect type : stored.getAspects()) {
            if (stored.getAmount(type) > 0 && AlembicBlockEntity.processColumn(level, base, type)) {
                takeFromContainer(type, 1); break;
            }
        }
    }
    @Override public boolean triggerEvent(int id, int value) {
        if (id == 1 && level != null && level.isClientSide) {
            Direction direction = Direction.from3DDataValue(value).getOpposite();
            double x = worldPosition.getX() + .5 + direction.getStepX(), z = worldPosition.getZ() + .5 + direction.getStepZ();
            level.playLocalSound(x, worldPosition.getY() + .5, z, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, .25F, 2.6F, false);
            for (int i = 0; i < 4; i++) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, worldPosition.getY() + .5, z, direction.getStepX() / 4D, .05, direction.getStepZ() / 4D);
            return true;
        }
        return super.triggerEvent(id, value);
    }
    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack removed = ContainerHelper.removeItem(items, slot, amount); if (!removed.isEmpty()) changed(); return removed; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); changed(); }
    @Override public void clearContent() { items.clear(); changed(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5) <= 64; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 ? AspectRegistry.getAspects(stack).size() > 0 : slot == 1 && fuelTicks(stack) > 0; }
    @Override public int[] getSlotsForFace(Direction face) { return face == Direction.UP ? NO_SLOTS : face == Direction.DOWN ? FUEL_SLOTS : INPUT_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) { return face != Direction.UP && canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return face != Direction.UP || slot != 1 || stack.is(Items.BUCKET); }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new SmelterMenu(id, playerInventory, this, data); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items); stored.writeToNBT(tag);
        tag.putInt("BurnTime", burnTime); tag.putInt("CurrentItemBurnTime", currentBurnTime); tag.putInt("CookTime", cookTime);
        tag.putBoolean("speedBoost", speedBoost); tag.putInt("SmeltTime", smeltTime);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(2, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        for (ItemStack item : items) if (item.getCount() > 64) item.setCount(64);
        AspectList loaded = new AspectList(); loaded.readFromNBT(tag); stored = bounded(loaded);
        burnTime = Math.max(0, Math.min(1000000, tag.getInt("BurnTime")));
        currentBurnTime = tag.contains("CurrentItemBurnTime") ? Math.max(0, Math.min(1000000, tag.getInt("CurrentItemBurnTime"))) : fuelTicks(getItem(1));
        cookTime = Math.max(0, Math.min(512, tag.getInt("CookTime")));
        smeltTime = tag.contains("SmeltTime") ? Math.max(1, Math.min(512, tag.getInt("SmeltTime"))) : 100;
        speedBoost = tag.getBoolean("speedBoost"); bellows = -1;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved() && capability == ForgeCapabilities.ITEM_HANDLER && side == null) return unsidedItems.cast();
        if (!isRemoved() && capability == ForgeCapabilities.ITEM_HANDLER && side != null) {
            int index = switch (side) { case DOWN -> 0; case UP -> 1; case NORTH -> 2; case SOUTH -> 3; case WEST -> 4; case EAST -> 5; };
            return itemHandlers[index].cast();
        }
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); unsidedItems.invalidate(); for (LazyOptional<IItemHandlerModifiable> handler : itemHandlers) handler.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps(); unsidedItems = LazyOptional.of(() -> new InvWrapper(this));
        itemHandlers = SidedInvWrapper.create(this, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    }
}
