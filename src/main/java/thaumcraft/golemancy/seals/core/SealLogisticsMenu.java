package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.research.KnowledgeStore;
import java.util.*;

/** BETA26 bell logistics: 81 ghost catalogue slots from owned provider seals within 32 blocks. */
public final class SealLogisticsMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final BlockPos target;
    private final Direction side;
    private final SimpleContainer display=new SimpleContainer(81){@Override public int getMaxStackSize(){return Integer.MAX_VALUE;}};
    private List<ItemStack> items=List.of();
    private int start,end,revision;
    private String search="",lastState="";
    private long lastRefresh;
    public SealLogisticsMenu(int id,Inventory inventory,FriendlyByteBuf data){this(id,inventory,readTarget(data));}
    private record Target(BlockPos pos,Direction side){}
    private static Target readTarget(FriendlyByteBuf data){return data.readBoolean()?new Target(data.readBlockPos(),data.readEnum(Direction.class)):new Target(null,null);}
    private SealLogisticsMenu(int id,Inventory inventory,Target target){this(id,inventory,target.pos(),target.side());}
    public SealLogisticsMenu(int id,Inventory inventory,BlockPos target,Direction side){
        super(SealRegistry.LOGISTICS_MENU.get(),id);this.inventory=inventory;this.target=target==null?null:target.immutable();this.side=side;
        for(int a=0;a<81;a++)addSlot(new Slot(display,a,19+a%9*19,19+a/9*19){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
            @Override public int getMaxStackSize(){return Integer.MAX_VALUE;}
        });
        if(inventory.player instanceof ServerPlayer player)refresh(player);
    }
    public BlockPos target(){return target;}
    public Direction side(){return side;}
    public int start(){return start;}
    public int end(){return end;}
    public int revision(){return revision;}
    public String search(){return search;}
    public List<ItemStack> entries(){List<ItemStack> result=new ArrayList<>();for(int i=0;i<81;i++)result.add(display.getItem(i).copy());return List.copyOf(result);}
    public void acceptSnapshot(int revision,int start,int end,List<ItemStack> entries){
        if(revision<this.revision||entries.size()>81||start<0||end<0)return;this.revision=revision;this.start=start;this.end=end;
        for(int i=0;i<81;i++)display.setItem(i,i<entries.size()?entries.get(i).copy():ItemStack.EMPTY);
    }
    @Override public boolean stillValid(Player player){
        if(!player.isAlive()||player.isSpectator())return false;
        if(player instanceof ServerPlayer server)return player.containerMenu==this&&player==inventory.player&&KnowledgeStore.get(server).isResearchKnown("GOLEMLOGISTICS")&&(target==null||side!=null&&server.serverLevel().hasChunkAt(target)&&player.distanceToSqr(target.getCenter())<=64&&server.serverLevel().mayInteract(player,target));
        return target==null||player.distanceToSqr(target.getCenter())<=64;
    }
    public boolean button(ServerPlayer player,int expected,int button,String search){
        if(player!=inventory.player||expected!=revision||!stillValid(player)||search==null||search.length()>128)return false;
        if(!this.search.equals(search)){this.search=search;start=0;refresh(player);}
        if(button==22){refresh(player);return true;}
        if(button==0){if(start<end){start++;refreshPage(player);}return true;}
        if(button==1){if(start>0){start--;refreshPage(player);}return true;}
        if(button>=100&&button-100<=end){start=button-100;refreshPage(player);return true;}return false;
    }
    private List<ItemStack> catalogue(ServerPlayer player){
        LinkedHashMap<String,ItemStack> collected=new LinkedHashMap<>();
        for(SealData seal:SealService.get(player.serverLevel()).inRange(player.blockPosition(),32)){
            if(!seal.type().equals("thaumcraft:provider")||!seal.owner().equals(player.getUUID())||!player.serverLevel().hasChunkAt(seal.position().pos()))continue;
            BlockEntity tile=player.serverLevel().getBlockEntity(seal.position().pos());if(tile==null||tile.isRemoved())continue;
            IItemHandler handler=tile.getCapability(ForgeCapabilities.ITEM_HANDLER,seal.position().face()).orElse(null);if(handler==null||handler.getSlots()>4096)continue;
            for(int slot=0;slot<handler.getSlots();slot++){
                ItemStack stack=handler.getStackInSlot(slot).copy();if(!seal.matches(stack)||!search.isEmpty()&&!stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
                String key=identity(stack);ItemStack old=collected.get(key);if(old==null){if(collected.size()>=4096)continue;collected.put(key,stack);}
                else old.setCount((int)Math.min(Integer.MAX_VALUE-64L,old.getCount()+(long)stack.getCount()));
            }
        }
        List<ItemStack> out=new ArrayList<>(collected.values());out.sort(Comparator.comparing((ItemStack s)->s.getHoverName().getString()).thenComparing(SealLogisticsMenu::identity));return out;
    }
    private static String identity(ItemStack stack){return ForgeRegistries.ITEMS.getKey(stack.getItem())+"|"+stack.getDamageValue()+"|"+stack.getTag();}
    public void refresh(ServerPlayer player){items=catalogue(player);end=Math.max(0,items.size()/9-8);start=Math.min(start,end);lastRefresh=player.serverLevel().getGameTime();refreshPage(player);}
    private void refreshPage(ServerPlayer player){
        for(int i=0;i<81;i++){int index=start*9+i;display.setItem(i,index<items.size()?items.get(index).copy():ItemStack.EMPTY);}
        // ItemStack.save writes Count as a byte. Catalogue totals use full integers,
        // so changes by 256 must still advance the physical menu's revision.
        String state=start+"|"+end+"|"+search+"|"+entries().stream().map(s->s.getCount()+"|"+identity(s)).toList();
        if(!lastState.equals(state)){lastState=state;revision=revision==Integer.MAX_VALUE?0:revision+1;}SealNetwork.snapshot(player,this);
    }
    /** Requests never extract inventory directly: a real provider task and golem must deliver the items. */
    public boolean request(ServerPlayer player,int expected,int slot,int amount){
        if(expected!=revision||!stillValid(player)||slot<0||slot>=81||amount<1||amount>65536)return false;
        ItemStack selected=display.getItem(slot).copy();if(selected.isEmpty()||selected.getCount()<amount)return false;
        // Reject stale inventory counts without taking a newly shifted slot's unrelated item template.
        List<ItemStack> current=catalogue(player);int count=0;for(ItemStack stack:current)if(ItemStack.isSameItemSameTags(selected,stack))count=stack.getCount();if(count<amount)return false;
        SealService service=SealService.get(player.serverLevel());int left=amount,ui=0;
        while(left>0){ItemStack stack=selected.copy();stack.setCount(Math.min(left,stack.getMaxStackSize()));left-=stack.getCount();
            if(target==null)service.requestProvision(player,stack,ui++);else service.requestProvision(target,side,stack,ui++);
        }return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){/* Catalogue ghosts must never change a real carried stack. */}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player player,int button){return player instanceof ServerPlayer server&&button(server,revision,button,search);}
    @Override public void broadcastChanges(){if(inventory.player instanceof ServerPlayer player&&stillValid(player)&&player.serverLevel().getGameTime()-lastRefresh>=20)refresh(player);}
}
