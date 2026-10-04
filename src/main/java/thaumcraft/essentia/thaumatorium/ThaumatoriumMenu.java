package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import java.util.*;

public final class ThaumatoriumMenu extends AbstractContainerMenu {
    public record RecipeView(ResourceLocation id,ItemStack output,AspectList cost,boolean selected) {}
    private final Container catalyst;
    private final Inventory inventory;
    private final BlockPos position;
    private List<RecipeView> views=List.of();
    private AspectList stored=new AspectList();
    private int capacity=1,revision;
    private String lastState="";
    public ThaumatoriumMenu(int id,Inventory inventory,FriendlyByteBuf data) {this(id,inventory,new SimpleContainer(1),data.readBlockPos());}
    public ThaumatoriumMenu(int id,Inventory inventory,ThaumatoriumBlockEntity tile) {this(id,inventory,tile,tile.getBlockPos());}
    private ThaumatoriumMenu(int id,Inventory inventory,Container catalyst,BlockPos position) {
        super(ThaumatoriumModule.MENU.get(),id);this.catalyst=catalyst;this.inventory=inventory;this.position=position;
        addSlot(new Slot(catalyst,0,55,24));
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(inventory,9+row*9+column,8+column*18,135+row*18));
        for(int column=0;column<9;column++)addSlot(new Slot(inventory,column,8+column*18,193));
    }
    public BlockPos position() {return position;}
    public int revision() {return revision;}
    public int capacity() {return capacity;}
    public List<RecipeView> recipes() {return views;}
    public AspectList stored() {return stored.copy();}
    public ThaumatoriumBlockEntity tile() {return catalyst instanceof ThaumatoriumBlockEntity tile?tile:null;}
    public void acceptSnapshot(int revision,int capacity,List<RecipeView> views,AspectList stored) {if(revision<this.revision)return;this.revision=revision;this.capacity=capacity;this.views=List.copyOf(views);this.stored=stored.copy();}
    public boolean select(ServerPlayer player,int expectedRevision,ResourceLocation recipe) {
        return expectedRevision==revision&&player.containerMenu==this&&stillValid(player)&&tile()!=null&&tile().toggleRecipe(player,recipe);
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if(!(inventory.player instanceof ServerPlayer player)||tile()==null||!stillValid(player))return;
        var tile=tile();var recipes=tile.availableRecipes(player);
        String signature=tile.getItem(0).save(new net.minecraft.nbt.CompoundTag())+"|"+tile.maxRecipes()+"|"+tile.selectedRecipes()+"|"+tile.getAspects().aspects+"|"+recipes.stream().map(entry->entry.id()+":"+entry.output().save(new net.minecraft.nbt.CompoundTag())+":"+entry.cost().aspects).toList();
        if(signature.equals(lastState))return;lastState=signature;revision++;
        List<RecipeView> snapshot=recipes.stream().map(entry->new RecipeView(entry.id(),entry.output().copy(),entry.cost().copy(),tile.selectedRecipes().contains(entry.id()))).toList();
        acceptSnapshot(revision,tile.maxRecipes(),snapshot,tile.getAspects());ThaumatoriumNetwork.snapshot(player,this);
    }
    @Override public boolean stillValid(Player player) {return catalyst instanceof ThaumatoriumBlockEntity tile?tile.stillValid(player):player.level().hasChunkAt(position)&&player.level().getBlockState(position).getBlock() instanceof ThaumatoriumBlock&&player.distanceToSqr(position.getCenter())<=64;}
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),copy=source.copy();
        if(index!=0) {if(!moveItemStackTo(source,0,1,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(source,1,37,false))return ItemStack.EMPTY;
        if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(source.getCount()==copy.getCount())return ItemStack.EMPTY;slot.onTake(player,source);return copy;
    }
}
