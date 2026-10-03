package thaumcraft.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** BETA26's single, universally accessible infusion slot; contents belong to the server. */
public final class InfusionPedestalBlockEntity extends BlockEntity implements WorldlyContainer {
    private ItemStack item = ItemStack.EMPTY;
    private LazyOptional<IItemHandler> unsided;
    private LazyOptional<IItemHandler>[] sided;

    public InfusionPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(InfusionModule.PEDESTAL.get(), pos, state);
        createHandlers();
    }

    @SuppressWarnings("unchecked")
    private void createHandlers() {
        unsided = LazyOptional.of(() -> new InvWrapper(this) {
            @Override public ItemStack getStackInSlot(int slot) { return super.getStackInSlot(slot).copy(); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return canMutate() ? super.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return canMutate() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        });
        sided = new LazyOptional[Direction.values().length];
        for (Direction direction : Direction.values())
            sided[direction.ordinal()] = LazyOptional.of(() -> new SidedInvWrapper(this, direction) {
                @Override public ItemStack getStackInSlot(int slot) { return super.getStackInSlot(slot).copy(); }
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return canMutate() ? super.insertItem(slot, stack, simulate) : stack;
                }
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return canMutate() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
                }
            });
    }

    private boolean canMutate() {
        return level == null || level instanceof ServerLevel server && server.getServer().isSameThread();
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server)
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return item.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? item : ItemStack.EMPTY; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && (stack.isEmpty() || item.isEmpty());
    }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == 0; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    /** A detached copy prevents the caller's held/reagent stack from becoming an inventory alias. */
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0 || !canMutate()) return;
        item = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        changed();
    }

    public void setItemFromInfusion(ItemStack stack) { setItem(0, stack); }

    @Nullable public BlockPos findInstabilityMitigator() {
        return level != null && getBlockState().getValue(InfusionPedestalBlock.CHARGE) > 0
                ? InfusionProtection.find(level, worldPosition) : null;
    }

    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0 || item.isEmpty() || !canMutate()) return ItemStack.EMPTY;
        ItemStack removed = item.split(amount);
        if (item.isEmpty()) item = ItemStack.EMPTY;
        changed();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        // Like the release's removeStackFromSlot, removal still sends the empty-slot sync.
        return removeItem(slot, 1);
    }

    @Override public void clearContent() {
        if (item.isEmpty() || !canMutate()) return;
        item = ItemStack.EMPTY;
        changed();
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var list = new net.minecraft.nbt.ListTag();
        if (!item.isEmpty()) {
            CompoundTag entry = item.save(new CompoundTag());
            entry.putByte("Slot", (byte) 0);
            list.add(entry);
        }
        tag.put("Items", list);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        item = ItemStack.EMPTY;
        // Invalid persisted counts/slots are discarded rather than admitting oversized inputs.
        for (Tag raw : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("Slot", Tag.TAG_BYTE) || entry.getByte("Slot") != 0
                    || !entry.contains("Count", Tag.TAG_BYTE) || entry.getByte("Count") != 1) continue;
            ItemStack candidate = ItemStack.of(entry);
            if (!candidate.isEmpty()) item = candidate.copy();
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1);
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved() && capability == ForgeCapabilities.ITEM_HANDLER)
            return (side == null ? unsided : sided[side.ordinal()]).cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        unsided.invalidate();
        for (var handler : sided) handler.invalidate();
    }
    @Override public void reviveCaps() { super.reviveCaps(); createHandlers(); }

    @Override public boolean triggerEvent(int id, int data) {
        if (id != 11 && id != 12 && id != 5) return super.triggerEvent(id, data);
        if (level != null && level.isClientSide) {
            // Native particle equivalents retain server event routing; the custom BETA26 FX is separate.
            double x = worldPosition.getX() + .5, y = worldPosition.getY() + 1, z = worldPosition.getZ() + .5;
            if (id == 5) {
                var dust = new DustParticleOptions(new Vector3f(1, .3F, .1F), .8F);
                for (int n = 0; n < 16; n++) {
                    double angle = n * Math.PI / 8;
                    level.addParticle(dust, x + Math.cos(angle) * .6, y - .5, z + Math.sin(angle) * .6, 0, .01, 0);
                }
            } else {
                for (int n = 0; n < 12; n++)
                    level.addParticle(id == 11 ? ParticleTypes.WITCH : ParticleTypes.POOF,
                            x, y, z, level.random.nextGaussian() * .04, .02, level.random.nextGaussian() * .04);
            }
        }
        return true;
    }
}
