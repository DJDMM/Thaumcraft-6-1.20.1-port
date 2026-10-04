package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.*;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.*;

/** Every access resolves the currently loaded base; never hold a destroyed/unloaded tile reference. */
public final class ThaumatoriumTopBlockEntity extends BlockEntity implements IAspectContainer,IEssentiaTransport,WorldlyContainer {
    private LazyOptional<IItemHandler> itemHandler=LazyOptional.of(()->new InvWrapper(this));
    public ThaumatoriumTopBlockEntity(BlockPos pos,BlockState state) {super(ThaumatoriumModule.TOP.get(),pos,state);}
    public ThaumatoriumBlockEntity base() {return !isRemoved()&&level!=null&&level.hasChunkAt(worldPosition.below())&&level.getBlockEntity(worldPosition.below()) instanceof ThaumatoriumBlockEntity tile&&!tile.isRemoved()?tile:null;}
    @Override public AspectList getAspects() {var tile=base();return tile==null?new AspectList():tile.getAspects();}
    @Override public void setAspects(AspectList list) {var tile=base();if(tile!=null)tile.setAspects(list);}
    @Override public boolean doesContainerAccept(Aspect aspect) {return true;}
    @Override public int addToContainer(Aspect aspect,int amount) {var tile=base();return tile==null?Math.max(0,amount):tile.addToContainer(aspect,amount);}
    @Override public boolean takeFromContainer(Aspect aspect,int amount) {var tile=base();return tile!=null&&tile.takeFromContainer(aspect,amount);}
    @Override public boolean takeFromContainer(AspectList list) {return false;}
    @Override public boolean doesContainerContain(AspectList list) {return false;}
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) {var tile=base();return tile!=null&&tile.doesContainerContainAmount(aspect,amount);}
    @Override public int containerContains(Aspect aspect) {var tile=base();return tile==null?0:tile.containerContains(aspect);}
    @Override public boolean isConnectable(Direction face) {var tile=base();return tile!=null&&tile.isConnectable(face);}
    @Override public boolean canInputFrom(Direction face) {var tile=base();return tile!=null&&tile.canInputFrom(face);}
    @Override public boolean canOutputTo(Direction face) {return false;}
    @Override public void setSuction(Aspect aspect,int amount) {var tile=base();if(tile!=null)tile.setSuction(aspect,amount);}
    @Override public Aspect getSuctionType(Direction face) {var tile=base();return tile==null?null:tile.getSuctionType(face);}
    @Override public int getSuctionAmount(Direction face) {var tile=base();return tile==null?0:tile.getSuctionAmount(face);}
    @Override public int takeEssentia(Aspect aspect,int amount,Direction face) {return 0;}
    @Override public int addEssentia(Aspect aspect,int amount,Direction face) {var tile=base();return tile==null?0:tile.addEssentia(aspect,amount,face);}
    @Override public Aspect getEssentiaType(Direction face) {return null;}
    @Override public int getEssentiaAmount(Direction face) {return 0;}
    @Override public int getMinimumSuction() {return 0;}
    @Override public int getContainerSize() {return 1;}
    @Override public boolean isEmpty() {return getItem(0).isEmpty();}
    @Override public ItemStack getItem(int slot) {var tile=base();return tile==null?ItemStack.EMPTY:tile.getItem(slot);}
    @Override public ItemStack removeItem(int slot,int amount) {var tile=base();return tile==null?ItemStack.EMPTY:tile.removeItem(slot,amount);}
    @Override public ItemStack removeItemNoUpdate(int slot) {var tile=base();return tile==null?ItemStack.EMPTY:tile.removeItemNoUpdate(slot);}
    @Override public void setItem(int slot,ItemStack stack) {var tile=base();if(tile!=null)tile.setItem(slot,stack);}
    @Override public void clearContent() {var tile=base();if(tile!=null)tile.clearContent();}
    @Override public boolean stillValid(Player player) {var tile=base();return tile!=null&&tile.stillValid(player);}
    @Override public int[] getSlotsForFace(Direction face) {return new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction face) {return slot==0;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction face) {return slot==0;}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {if(!isRemoved()&&capability==ForgeCapabilities.ITEM_HANDLER)return itemHandler.cast();return super.getCapability(capability,side);}
    @Override public void invalidateCaps() {super.invalidateCaps();itemHandler.invalidate();}
    @Override public void reviveCaps() {super.reviveCaps();itemHandler=LazyOptional.of(()->new InvWrapper(this));}
}
