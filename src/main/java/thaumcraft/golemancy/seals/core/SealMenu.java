package thaumcraft.golemancy.seals.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Original seal settings and ghost filters, with physical-context/revision server validation. */
public final class SealMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final SealPos position;
    private final SimpleContainer ghosts;
    private SealData seal;
    private int category,revision;
    private String lastState="";
    public SealMenu(int id,Inventory inventory,FriendlyByteBuf data){this(id,inventory,Objects.requireNonNull(SealData.load(Objects.requireNonNull(data.readNbt()))));}
    public SealMenu(int id,Inventory inventory,SealData seal){
        super(SealRegistry.MENU.get(),id);this.inventory=inventory;this.seal=seal;position=seal.position();revision=seal.revision();
        category=categories().get(0);ghosts=new SimpleContainer(seal.filters().size());
        int size=ghosts.getContainerSize(),sx=16+(size-1)%3*12,sy=16+(size-1)/3*12;
        for(int a=0;a<size;a++){
            final int index=a;addSlot(new Slot(ghosts,a,88+a%3*24-sx+8,72+a/3*24-sy+8){
                @Override public boolean mayPlace(ItemStack stack){return false;}
                @Override public boolean mayPickup(Player player){return false;}
                @Override public boolean isActive(){return category==1;}
                @Override public ItemStack getItem(){return SealMenu.this.seal.filter(index);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,8+col*18,150+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,208));
        lastState=state();
    }
    public SealPos position(){return position;}
    public SealData seal(){return seal;}
    public int category(){return category;}
    public List<Integer> categories(){SealBehavior behavior=SealRegistry.behavior(seal.type());return behavior==null?List.of(0):behavior.categories();}
    public int revision(){return revision;}
    public int filterSlots(){return ghosts.getContainerSize();}
    public void acceptSnapshot(int revision,int category,CompoundTag tag){
        if(revision<this.revision)return;SealData next=SealData.load(tag);if(next==null||!next.position().equals(position)||!next.type().equals(seal.type()))return;
        this.revision=revision;this.seal=next;if(categories().contains(category))this.category=category;lastState=state();
    }
    @Override public boolean stillValid(Player player){return player instanceof ServerPlayer server?player.containerMenu==this&&SealService.get(server.serverLevel()).canConfigure(server,seal):player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(position.pos().getCenter())<=64;}
    private boolean context(ServerPlayer player,int expected){return player==inventory.player&&expected==revision&&stillValid(player)&&SealService.get(player.serverLevel()).seal(position)==seal;}
    private String state(){return category+"|"+seal.save();}
    private void changed(ServerPlayer player,boolean persistent){if(persistent)SealService.get(player.serverLevel()).changed(seal);revision=revision==Integer.MAX_VALUE?0:revision+1;lastState=state();SealNetwork.snapshot(player,this);}
    public boolean button(ServerPlayer player,int expected,int action){
        if(!context(player,expected))return false;boolean persistent=true,accepted=false;
        List<Integer> categories=categories();SealBehavior behavior=SealRegistry.behavior(seal.type());
        if(action>=0&&action<categories.size()){category=categories.get(action);persistent=false;accepted=true;}
        else if(category==3&&action>=30&&action<30+seal.toggles().size()){String key=new ArrayList<>(seal.toggles().keySet()).get(action-30);seal.toggle(key,true);accepted=true;}
        else if(category==3&&action>=60&&action<60+seal.toggles().size()){String key=new ArrayList<>(seal.toggles().keySet()).get(action-60);seal.toggle(key,false);accepted=true;}
        else if(category==0&&(action==25||action==26)&&seal.owner().equals(player.getUUID())){seal.locked(action==25);accepted=true;}
        else if((action==27||action==28)&&seal.owner().equals(player.getUUID())){seal.redstone(action==27);accepted=true;}
        else if(category==1&&filterSlots()>0&&(action==20||action==21)){seal.blacklist(action==20);accepted=true;}
        else if(action==80&&seal.priority()>-5){seal.priority(seal.priority()-1);accepted=true;}
        else if(action==81&&seal.priority()<5){seal.priority(seal.priority()+1);accepted=true;}
        else if(action==82&&seal.color()>0){seal.color(seal.color()-1);accepted=true;}
        else if(action==83&&seal.color()<16){seal.color(seal.color()+1);accepted=true;}
        else if(behavior!=null&&behavior.hasArea()){
            var area=seal.area();switch(action){
                case 90->{if(area.getY()>1){seal.area(area.offset(0,-1,0));accepted=true;}}
                case 91->{if(area.getY()<8){seal.area(area.offset(0,1,0));accepted=true;}}
                case 92->{if(area.getX()>1){seal.area(area.offset(-1,0,0));accepted=true;}}
                case 93->{if(area.getX()<8){seal.area(area.offset(1,0,0));accepted=true;}}
                case 94->{if(area.getZ()>1){seal.area(area.offset(0,0,-1));accepted=true;}}
                case 95->{if(area.getZ()<8){seal.area(area.offset(0,0,1));accepted=true;}}
            }
        }
        if(accepted)changed(player,persistent);return accepted;
    }
    public boolean ghostClick(ServerPlayer player,int expected,int slot,int button,boolean quick){
        if(!context(player,expected)||category!=1||slot<0||slot>=filterSlots()||button<0||button>1)return false;
        SealBehavior behavior=SealRegistry.behavior(seal.type());boolean limited=behavior!=null&&behavior.hasStacksizeLimiters();ItemStack carried=getCarried().copy(),old=seal.filter(slot);
        int size=seal.filterSize(slot),step=quick?10:1;
        if(button==1){
            if(!limited){seal.filter(slot,ItemStack.EMPTY);seal.filterSize(slot,0);}
            else if(carried.isEmpty()&&!old.isEmpty()){if(size<step){seal.filter(slot,ItemStack.EMPTY);seal.filterSize(slot,0);}else seal.filterSize(slot,size-step);}
            else if(!carried.isEmpty()&&!old.isEmpty()&&ItemStack.isSameItemSameTags(carried,old)){if(size<carried.getCount()){seal.filter(slot,ItemStack.EMPTY);seal.filterSize(slot,0);}else seal.filterSize(slot,size-carried.getCount());}
        }else if(carried.isEmpty()){if(limited&&!old.isEmpty())seal.filterSize(slot,(int)Math.min(Integer.MAX_VALUE-64L,size+(long)step));}
        else{
            if(!limited)seal.filterSize(slot,0);
            else seal.filterSize(slot,!old.isEmpty()&&ItemStack.isSameItemSameTags(carried,old)?(int)Math.min(Integer.MAX_VALUE-64L,size+(long)carried.getCount()):0);
            seal.filter(slot,carried);
        }
        changed(player,true);return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(slot>=0&&slot<filterSlots()){
            if(player instanceof ServerPlayer server&&(type==ClickType.PICKUP||type==ClickType.QUICK_MOVE))ghostClick(server,revision,slot,button,type==ClickType.QUICK_MOVE);
            return;
        }if(stillValid(player))super.clicked(slot,button,type,player);
    }
    @Override public boolean clickMenuButton(Player player,int action){return player instanceof ServerPlayer server&&button(server,revision,action);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(inventory.player instanceof ServerPlayer player)||!stillValid(player))return;
        String state=state();if(state.equals(lastState))return;lastState=state;revision=revision==Integer.MAX_VALUE?0:revision+1;SealNetwork.snapshot(player,this);
    }
}
