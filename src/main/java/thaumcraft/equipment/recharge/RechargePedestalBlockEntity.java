package thaumcraft.equipment.recharge;

import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import thaumcraft.api.aspects.*;
import thaumcraft.api.items.IRechargable;
import thaumcraft.equipment.RechargeSupport;

/** One-slot BETA26 pedestal, recharging up to five vis every ten ticks. */
public final class RechargePedestalBlockEntity extends BlockEntity implements WorldlyContainer,IAspectContainer {
    private final NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    private LazyOptional<IItemHandler> handler=LazyOptional.of(() -> new InvWrapper(this));
    private int counter;
    public RechargePedestalBlockEntity(BlockPos pos,BlockState state) { super(RechargeModule.PEDESTAL.get(),pos,state); }
    public static void tick(Level level,BlockPos pos,BlockState state,RechargePedestalBlockEntity pedestal) {
        if(level instanceof ServerLevel server && pedestal.counter++%10==0
                && RechargeSupport.rechargeFromAura(server,pedestal.getItem(0),pos,null,5)>0) {
            pedestal.changed();
            var primals=Aspect.getPrimalAspects();
            level.blockEvent(pos,state.getBlock(),5,primals.get(level.random.nextInt(primals.size())).getColor());
        }
    }
    private void changed() {
        setChanged();
        if(level!=null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot==0 ? items.get(0) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot,int amount) {
        ItemStack removed=ContainerHelper.removeItem(items,slot,amount);
        if(!removed.isEmpty()) changed();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed=ContainerHelper.takeItem(items,slot);
        if(!removed.isEmpty()) changed();
        return removed;
    }
    @Override public void setItem(int slot,ItemStack stack) {
        if(slot!=0) return;
        items.set(slot,stack); if(stack.getCount()>1) stack.setCount(1); changed();
    }
    @Override public void clearContent() { items.set(0,ItemStack.EMPTY);changed(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this,player); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot==0 && stack.getItem() instanceof IRechargable; }
    @Override public int[] getSlotsForFace(Direction direction) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction face) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction face) { return slot==0; }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,items); }
    @Override public void load(CompoundTag tag) { super.load(tag);items.set(0,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        return !isRemoved() && capability==ForgeCapabilities.ITEM_HANDLER ? handler.cast() : super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps();handler.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps();handler=LazyOptional.of(() -> new InvWrapper(this)); }
    @Override public boolean triggerEvent(int id,int color) {
        if(id!=5) return super.triggerEvent(id,color);
        if(level!=null && level.isClientSide) {
            Vector3f rgb=new Vector3f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);
            level.addParticle(new DustParticleOptions(rgb,.5F),worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5,0,.01,0);
        }
        return true;
    }
    @Override public AspectList getAspects() {
        return getItem(0).getItem() instanceof IRechargable ? new AspectList().add(Aspect.ENERGY,Math.max(0,RechargeSupport.getCharge(getItem(0)))) : null;
    }
    @Override public void setAspects(AspectList aspects) {}
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect,int amount) { return 0; }
    @Override public boolean takeFromContainer(Aspect aspect,int amount) { return false; }
    @Override @Deprecated public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) { return false; }
    @Override @Deprecated public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public int containerContains(Aspect aspect) { return 0; }
}
