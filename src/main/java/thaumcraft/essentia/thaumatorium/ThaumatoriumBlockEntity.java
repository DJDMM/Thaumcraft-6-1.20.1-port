package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
import thaumcraft.alchemy.CrucibleRecipes;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.*;
import java.util.*;

/** BETA26 one catalyst slot, ordered selected recipes, typed 128 suction and one-unit fifth-tick draw. */
public final class ThaumatoriumBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider,IAspectContainer,IEssentiaTransport {
    private NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    private AspectList essentia=new AspectList();
    private final List<ResourceLocation> selected=new ArrayList<>();
    private final List<String> owners=new ArrayList<>();
    private final List<ItemStack> clientOutputs=new ArrayList<>();
    private int maxRecipes=1,counter,currentCraft=-1,venting;
    private boolean heated,completing,ejecting;
    private Aspect currentSuction;
    private ItemStack pendingOutput=ItemStack.EMPTY;
    private LazyOptional<IItemHandler> itemHandler=LazyOptional.of(()->new InvWrapper(this));
    public ThaumatoriumBlockEntity(BlockPos pos,BlockState state) { super(ThaumatoriumModule.BASE.get(),pos,state); }
    public Direction facing() { return getBlockState().getValue(ThaumatoriumBlock.FACING); }
    public List<ResourceLocation> selectedRecipes() { return List.copyOf(selected); }
    public int maxRecipes() { return maxRecipes; }
    public boolean heated() { return heated; }
    public ItemStack pendingOutput() { return pendingOutput.copy(); }
    public int currentCraft() { return currentCraft; }
    public static CrucibleRecipes.Entry recipe(ResourceLocation id) { return CrucibleRecipes.all().stream().filter(entry->entry.id().equals(id)).findFirst().orElse(null); }
    private CrucibleRecipes.Entry currentRecipe() { return currentCraft>=0&&currentCraft<selected.size()?recipe(selected.get(currentCraft)):null; }
    public ItemStack cyclingOutput(long ticks) {
        if(selected.isEmpty())return ItemStack.EMPTY;
        int index=(int)Math.floorMod(ticks/20,selected.size());
        if(level!=null&&level.isClientSide)return index<clientOutputs.size()?clientOutputs.get(index).copy():ItemStack.EMPTY;
        var entry=recipe(selected.get(index));return entry==null?ItemStack.EMPTY:entry.output().copy();
    }
    public List<CrucibleRecipes.Entry> availableRecipes(ServerPlayer player) {
        if(getItem(0).isEmpty())return List.of();
        var knowledge=KnowledgeStore.get(player);
        return CrucibleRecipes.all().stream().filter(entry->selected.contains(entry.id())||(entry.matchesCatalyst(getItem(0))&&knowledge.isResearchCompleteStrict(entry.research())))
                .sorted(Comparator.comparing(entry->entry.output().getHoverName().getString())).limit(256).toList();
    }
    /** Server re-derives discovery on every packet; a selected recipe can always be removed. */
    public boolean toggleRecipe(ServerPlayer player,ResourceLocation id) {
        if(!(level instanceof ServerLevel)||player==null||player.isSpectator()||!stillValid(player)||completing)return false;
        int index=selected.indexOf(id);
        if(index>=0) {selected.remove(index);owners.remove(index);currentCraft=-1;currentSuction=null;changed();return true;}
        if(selected.size()>=maxRecipes||availableRecipes(player).stream().noneMatch(entry->entry.id().equals(id)))return false;
        selected.add(id);owners.add(player.getGameProfile().getName());changed();return true;
    }
    private boolean loaded(BlockPos pos) { return level!=null&&level.hasChunkAt(pos); }
    public boolean checkHeat() {
        BlockPos heat=worldPosition.below(2);
        if(!loaded(heat))return false;
        var state=level.getBlockState(heat);
        var key=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return state.is(Blocks.LAVA)||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.MAGMA_BLOCK)||state.is(thaumcraft.alchemy.AlchemyModule.NITOR.get())||key!=null&&key.getNamespace().equals("thaumcraft")&&key.getPath().startsWith("nitor_");
    }
    public boolean gettingPower() {
        return level!=null&&level.hasNeighborSignal(worldPosition)||loaded(worldPosition.below())&&level.hasNeighborSignal(worldPosition.below())||loaded(worldPosition.above())&&level.hasNeighborSignal(worldPosition.above());
    }
    public void getUpgrades() {
        if(level==null||level.isClientSide)return;
        boolean removed=false;
        for(int index=selected.size()-1;index>=0;index--)if(recipe(selected.get(index))==null) {selected.remove(index);owners.remove(index);currentCraft=-1;removed=true;}
        int capacity=1;
        for(int y=0;y<=1;y++)for(Direction side:Direction.values()) {
            if(side==Direction.DOWN||side==facing())continue;
            BlockPos pos=worldPosition.above(y).relative(side);
            if(loaded(pos)&&level.getBlockState(pos).getBlock() instanceof BrainBoxBlock&&level.getBlockState(pos).getValue(BrainBoxBlock.FACING)==side.getOpposite())capacity+=2;
        }
        capacity=Math.min(17,capacity);
        if(capacity!=maxRecipes) {
            maxRecipes=capacity;
            while(selected.size()>maxRecipes) {selected.remove(selected.size()-1);owners.remove(owners.size()-1);}
            if(currentCraft>=selected.size())currentCraft=-1;
            changed();
        }
        else if(removed)changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ThaumatoriumBlockEntity tile) {
        if(tile.isRemoved()||level.getBlockEntity(pos)!=tile)return;
        if(level.isClientSide) {
            if(tile.venting>0) {tile.venting--;Direction side=tile.facing();level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,pos.getX()+.5+side.getStepX()*.5,pos.getY()+.5,pos.getZ()+.5+side.getStepZ()*.5,side.getStepX()*.25,.01,side.getStepZ()*.25);}
            return;
        }
        if(tile.counter==0||tile.counter%40==0) {tile.heated=tile.checkHeat();tile.getUpgrades();}
        tile.counter=tile.counter==Integer.MAX_VALUE?1:tile.counter+1;
        if(!tile.pendingOutput.isEmpty()) { if(tile.counter%5==0)tile.ejectPending();return; }
        if(!tile.heated||tile.gettingPower()||tile.counter%5!=0||tile.selected.isEmpty())return;
        if(tile.getItem(0).isEmpty()) {tile.currentSuction=null;return;}
        var current=tile.currentRecipe();
        if(current==null||!current.matchesCatalyst(tile.getItem(0))) {
            for(int index=0;index<tile.selected.size();index++) {var candidate=recipe(tile.selected.get(index));if(candidate!=null&&candidate.matchesCatalyst(tile.getItem(0))) {tile.currentCraft=index;current=candidate;break;}}
        }
        // BETA26 retains its previous active selection if the replacement catalyst matches none.
        if(current==null)return;
        tile.currentSuction=null;
        for(Aspect aspect:current.cost().getAspectsSortedByName())if(tile.essentia.getAmount(aspect)<current.cost().getAmount(aspect)) {tile.currentSuction=aspect;break;}
        if(tile.currentSuction==null)tile.completeRecipe();else tile.fill();
    }
    private void fill() {
        for(int y=0;y<=1;y++)for(Direction side:Direction.values()) {
            if(side==facing()||side==Direction.DOWN||y==0&&side==Direction.UP)continue;
            BlockPos source=worldPosition.above(y).relative(side);
            if(!loaded(source)||!(level.getBlockEntity(source) instanceof IEssentiaTransport transport)||!transport.isConnectable(side.getOpposite()))continue;
            if(transport.getEssentiaAmount(side.getOpposite())>0&&transport.getSuctionAmount(side.getOpposite())<getSuctionAmount(null)&&getSuctionAmount(null)>=transport.getMinimumSuction()) {
                int units=transport.takeEssentia(currentSuction,1,side.getOpposite());
                if(units>0) {addToContainer(currentSuction,units);return;}
            }
        }
    }
    private void completeRecipe() {
        var entry=currentRecipe();
        if(completing||entry==null||!entry.matchesCatalyst(getItem(0))||!entry.hasAspects(essentia)||!(level instanceof ServerLevel server))return;
        completing=true;
        try {
            // TileThaumatorium clears ALL buffered aspects and consumes one catalyst, with no container remainder.
            String owner=owners.get(currentCraft);getItem(0).shrink(1);essentia=new AspectList();pendingOutput=entry.output().copy();currentCraft=-1;changed();
            ServerPlayer player=server.getServer().getPlayerList().getPlayerByName(owner);
            if(player!=null&&player.serverLevel()==server)net.minecraftforge.event.ForgeEventFactory.firePlayerCraftingEvent(player,entry.output().copy(),new SimpleContainer(getItem(0).copy()));
            ejectPending();
            server.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,.25f,2.6f+(server.random.nextFloat()-server.random.nextFloat())*.8f);
        } finally {completing=false;}
    }
    /** Original inventory-first ejection; retain only a vetoed overflow instead of losing or duplicating paid output. */
    private void ejectPending() {
        if(!(level instanceof ServerLevel server)||pendingOutput.isEmpty()||isRemoved()||ejecting)return;
        Direction side=facing();BlockPos front=worldPosition.relative(side);
        if(!loaded(front))return;
        ejecting=true;ItemStack output=pendingOutput.copy();pendingOutput=ItemStack.EMPTY;changed();
        try {
        var other=server.getBlockEntity(front);
        if(other!=null) {var handler=other.getCapability(ForgeCapabilities.ITEM_HANDLER,side.getOpposite()).orElse(null);if(handler!=null)output=ItemHandlerHelper.insertItemStacked(handler,output,false);}
        if(!output.isEmpty()) {
            BlockPos origin=server.getBlockState(front).isCollisionShapeFullBlock(server,front)?worldPosition.relative(side.getOpposite()):worldPosition;
            var entity=new ItemEntity(server,origin.getX()+.5+side.getStepX(),origin.getY(),origin.getZ()+.5+side.getStepZ(),output.copy());
            entity.setDeltaMovement(side.getStepX()*.3,0,side.getStepZ()*.3);entity.setDefaultPickUpDelay();
            if(!server.addFreshEntity(entity)&&!isRemoved())pendingOutput=output;
        }
        changed();
        } finally {ejecting=false;}
    }
    public void dropPendingOutput() { if(!pendingOutput.isEmpty()&&level!=null) {Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),pendingOutput);pendingOutput=ItemStack.EMPTY;} }
    public void changed() {
        setChanged();
        if(level!=null&&!level.isClientSide) {level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
    }
    @Override public boolean triggerEvent(int id,int value) { if(id>=0&&level!=null&&level.isClientSide) {venting=7;return true;}return super.triggerEvent(id,value); }
    @Override public AspectList getAspects() { return essentia.copy(); }
    @Override public void setAspects(AspectList list) { if(level!=null&&level.isClientSide)return;essentia=bounded(list);changed(); }
    private static AspectList bounded(AspectList list) {var result=new AspectList();if(list!=null)for(Aspect aspect:list.getAspects())if(aspect!=null&&list.getAmount(aspect)>0)result.add(aspect,Math.min(500,list.getAmount(aspect)));return result;}
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect,int amount) {
        if(level!=null&&level.isClientSide||aspect==null||amount<=0)return Math.max(0,amount);
        var entry=currentRecipe();if(entry==null)return amount;
        int add=Math.min(amount,Math.max(0,entry.cost().getAmount(aspect)-essentia.getAmount(aspect)));
        if(add>0) {essentia.add(aspect,add);changed();}return amount-add;
    }
    @Override public boolean takeFromContainer(Aspect aspect,int amount) {if(level!=null&&level.isClientSide||aspect==null||amount<=0||essentia.getAmount(aspect)<amount)return false;essentia.remove(aspect,amount);changed();return true;}
    @Override public boolean takeFromContainer(AspectList list) {return false;}
    @Override public boolean doesContainerContain(AspectList list) {return false;}
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount) {return aspect!=null&&amount>=0&&essentia.getAmount(aspect)>=amount;}
    @Override public int containerContains(Aspect aspect) {return aspect==null?0:essentia.getAmount(aspect);}
    @Override public boolean isConnectable(Direction face) {return face!=facing();}
    @Override public boolean canInputFrom(Direction face) {return face!=facing();}
    @Override public boolean canOutputTo(Direction face) {return false;}
    @Override public void setSuction(Aspect aspect,int amount) {currentSuction=aspect;}
    @Override public Aspect getSuctionType(Direction face) {return currentSuction;}
    @Override public int getSuctionAmount(Direction face) {return currentSuction==null?0:128;}
    @Override public int takeEssentia(Aspect aspect,int amount,Direction face) {return 0;}
    @Override public int addEssentia(Aspect aspect,int amount,Direction face) {return canInputFrom(face)&&amount>0?amount-addToContainer(aspect,amount):0;}
    @Override public Aspect getEssentiaType(Direction face) {return null;}
    @Override public int getEssentiaAmount(Direction face) {return 0;}
    @Override public int getMinimumSuction() {return 0;}
    @Override public int getContainerSize() {return 1;}
    @Override public boolean isEmpty() {return getItem(0).isEmpty();}
    @Override public ItemStack getItem(int slot) {return slot==0?items.get(0):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int amount) {if(slot!=0||amount<=0)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(items,slot,amount);if(!result.isEmpty())changed();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot) {return slot==0?ContainerHelper.takeItem(items,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack) {if(slot!=0)return;items.set(0,stack);if(stack.getCount()>64)stack.setCount(64);changed();}
    @Override public void clearContent() {items.set(0,ItemStack.EMPTY);changed();}
    @Override public boolean stillValid(Player player) {return !isRemoved()&&level!=null&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&player.level()==level&&player.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public int[] getSlotsForFace(Direction face) {return new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction face) {return slot==0;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction face) {return slot==0;}
    @Override public Component getDisplayName() {return Component.translatable("block.thaumcraft.thaumatorium");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) {return new ThaumatoriumMenu(id,inventory,this);}
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,items);essentia.writeToNBT(tag);tag.putByte("maxrec",(byte)maxRecipes);
        ListTag ids=new ListTag(),names=new ListTag(),outputs=new ListTag();
        for(int index=0;index<selected.size();index++) {ids.add(StringTag.valueOf(selected.get(index).toString()));names.add(StringTag.valueOf(owners.get(index)));var entry=recipe(selected.get(index));ItemStack output=entry==null?index<clientOutputs.size()?clientOutputs.get(index):ItemStack.EMPTY:entry.output();outputs.add(output.save(new CompoundTag()));}
        tag.put("RecipeIds",ids);tag.put("OutputPlayer",names);tag.put("DisplayOutputs",outputs);if(!pendingOutput.isEmpty())tag.put("PendingOutput",pendingOutput.save(new CompoundTag()));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);items=NonNullList.withSize(1,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items);if(getItem(0).getCount()>64)getItem(0).setCount(64);
        var loaded=new AspectList();loaded.readFromNBT(tag);essentia=bounded(loaded);maxRecipes=Math.max(1,Math.min(17,tag.getByte("maxrec")));
        selected.clear();owners.clear();clientOutputs.clear();var ids=tag.getList("RecipeIds",Tag.TAG_STRING);var names=tag.getList("OutputPlayer",Tag.TAG_STRING);var outputs=tag.getList("DisplayOutputs",Tag.TAG_COMPOUND);
        for(int index=0;index<Math.min(17,ids.size());index++) {var id=ResourceLocation.tryParse(ids.getString(index));if(id==null||selected.contains(id))continue;selected.add(id);owners.add(index<names.size()?names.getString(index):"");clientOutputs.add(index<outputs.size()?ItemStack.of(outputs.getCompound(index)):ItemStack.EMPTY);}
        pendingOutput=tag.contains("PendingOutput",Tag.TAG_COMPOUND)?ItemStack.of(tag.getCompound("PendingOutput")):ItemStack.EMPTY;if(pendingOutput.getCount()>pendingOutput.getMaxStackSize())pendingOutput.setCount(pendingOutput.getMaxStackSize());
        counter=0;currentCraft=-1;currentSuction=null;heated=false;
    }
    @Override public CompoundTag getUpdateTag() {return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {return new net.minecraft.world.phys.AABB(worldPosition.getX()-.1,worldPosition.getY()-.1,worldPosition.getZ()-.1,worldPosition.getX()+1.1,worldPosition.getY()+2.1,worldPosition.getZ()+1.1);}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {if(!isRemoved()&&capability==ForgeCapabilities.ITEM_HANDLER)return itemHandler.cast();return super.getCapability(capability,side);}
    @Override public void invalidateCaps() {super.invalidateCaps();itemHandler.invalidate();}
    @Override public void reviveCaps() {super.reviveCaps();itemHandler=LazyOptional.of(()->new InvWrapper(this));}
}
