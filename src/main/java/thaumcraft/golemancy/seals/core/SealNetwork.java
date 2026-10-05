package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

/** C2S requests name a physical menu/revision; filter templates and logistics targets stay server-owned. */
public final class SealNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("thaumcraft","golem_seals"),()->"1","1"::equals,"1"::equals);
    private static boolean registered;
    public record Button(int menu,int revision,int action){}
    public record Filter(int menu,int revision,int slot,int button,boolean quick){}
    public record Logistics(int menu,int revision,int action,String search){}
    public record Request(int menu,int revision,int slot,int amount){}
    public record MenuSnapshot(int menu,int revision,int category,CompoundTag state){public MenuSnapshot{state=state.copy();}}
    public record LogisticsSnapshot(int menu,int revision,int start,int end,List<ItemStack> entries){public LogisticsSnapshot{entries=entries.stream().map(ItemStack::copy).toList();}}
    public record WorldSnapshot(ResourceLocation dimension,boolean replace,List<CompoundTag> seals){public WorldSnapshot{seals=seals.stream().map(CompoundTag::copy).toList();}}
    /** Optional client adapters; the default route uses root-owned SealClientState and the physical menus. */
    public static Consumer<WorldSnapshot> worldReceiver;
    public static Consumer<MenuSnapshot> menuReceiver;
    public static Consumer<LogisticsSnapshot> logisticsReceiver;
    private SealNetwork(){}
    public static void register(){
        if(registered)return;registered=true;
        CHANNEL.messageBuilder(Button.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.action());}).decoder(b->new Button(b.readVarInt(),b.readVarInt(),b.readVarInt())).consumerMainThread((p,c)->{process(c.get().getSender(),p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Filter.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.slot());b.writeVarInt(p.button());b.writeBoolean(p.quick());}).decoder(b->new Filter(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean())).consumerMainThread((p,c)->{process(c.get().getSender(),p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Logistics.class,2,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.action());b.writeUtf(p.search(),128);}).decoder(b->new Logistics(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(128))).consumerMainThread((p,c)->{process(c.get().getSender(),p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Request.class,3,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.slot());b.writeVarInt(p.amount());}).decoder(b->new Request(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt())).consumerMainThread((p,c)->{process(c.get().getSender(),p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(MenuSnapshot.class,4,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.category());b.writeNbt(p.state());}).decoder(b->{int menu=b.readVarInt(),revision=b.readVarInt(),category=b.readVarInt();CompoundTag tag=b.readNbt();if(revision<0||tag==null)throw new IllegalArgumentException("Seal snapshot");return new MenuSnapshot(menu,revision,category,tag);}).consumerMainThread((p,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->accept(p));c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(LogisticsSnapshot.class,5,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeVarInt(p.start());b.writeVarInt(p.end());b.writeVarInt(p.entries().size());for(ItemStack stack:p.entries())writeLargeStack(b,stack);}).decoder(b->{int menu=b.readVarInt(),revision=b.readVarInt(),start=b.readVarInt(),end=b.readVarInt(),size=b.readVarInt();if(revision<0||size<0||size>81||start<0||end<0||end>456)throw new IllegalArgumentException("Logistics snapshot bounds");List<ItemStack> entries=new ArrayList<>();for(int i=0;i<size;i++)entries.add(readLargeStack(b));return new LogisticsSnapshot(menu,revision,start,end,entries);}).consumerMainThread((p,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->accept(p));c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(WorldSnapshot.class,6,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeResourceLocation(p.dimension());b.writeBoolean(p.replace());b.writeVarInt(p.seals().size());for(CompoundTag seal:p.seals())b.writeNbt(seal);}).decoder(b->{ResourceLocation dimension=b.readResourceLocation();boolean replace=b.readBoolean();int size=b.readVarInt();if(size<0||size>1024)throw new IllegalArgumentException("World seal bounds");List<CompoundTag> seals=new ArrayList<>();for(int i=0;i<size;i++)seals.add(Objects.requireNonNull(b.readNbt()));return new WorldSnapshot(dimension,replace,seals);}).consumerMainThread((p,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->accept(p));c.get().setPacketHandled(true);}).add();
    }
    private static void writeLargeStack(FriendlyByteBuf buffer,ItemStack stack){ItemStack template=stack.copy();if(!template.isEmpty())template.setCount(1);buffer.writeItem(template);buffer.writeVarInt(stack.getCount());}
    private static ItemStack readLargeStack(FriendlyByteBuf buffer){ItemStack stack=buffer.readItem();int amount=buffer.readVarInt();if(amount<0||amount>Integer.MAX_VALUE-64||stack.isEmpty()&&amount!=0)throw new IllegalArgumentException("Logistics count");if(!stack.isEmpty())stack.setCount(amount);return stack;}
    private static void accept(MenuSnapshot snapshot){
        if(menuReceiver!=null){menuReceiver.accept(snapshot);return;}var minecraft=net.minecraft.client.Minecraft.getInstance();if(minecraft.player!=null&&minecraft.player.containerMenu instanceof SealMenu menu&&menu.containerId==snapshot.menu())menu.acceptSnapshot(snapshot.revision(),snapshot.category(),snapshot.state());
    }
    private static void accept(LogisticsSnapshot snapshot){
        if(logisticsReceiver!=null){logisticsReceiver.accept(snapshot);return;}var minecraft=net.minecraft.client.Minecraft.getInstance();if(minecraft.player!=null&&minecraft.player.containerMenu instanceof SealLogisticsMenu menu&&menu.containerId==snapshot.menu())menu.acceptSnapshot(snapshot.revision(),snapshot.start(),snapshot.end(),snapshot.entries());
    }
    private static void accept(WorldSnapshot snapshot){
        if(worldReceiver!=null){worldReceiver.accept(snapshot);return;}var minecraft=net.minecraft.client.Minecraft.getInstance();if(minecraft.level==null||!minecraft.level.dimension().location().equals(snapshot.dimension()))return;
        if(snapshot.replace())thaumcraft.golemancy.client.SealClientState.replace(snapshot.seals());
        else for(CompoundTag tag:snapshot.seals())thaumcraft.golemancy.client.SealClientState.update(tag,tag.getBoolean("Removed"));
    }
    public static boolean process(ServerPlayer player,Button request){if(player==null||request.menu()<0||request.revision()<0||!(player.containerMenu instanceof SealMenu menu)||menu.containerId!=request.menu())return false;boolean accepted=menu.button(player,request.revision(),request.action());snapshot(player,menu);return accepted;}
    public static boolean process(ServerPlayer player,Filter request){if(player==null||request.menu()<0||request.revision()<0||!(player.containerMenu instanceof SealMenu menu)||menu.containerId!=request.menu())return false;boolean accepted=menu.ghostClick(player,request.revision(),request.slot(),request.button(),request.quick());snapshot(player,menu);return accepted;}
    public static boolean process(ServerPlayer player,Logistics request){if(player==null||request.menu()<0||request.revision()<0||!(player.containerMenu instanceof SealLogisticsMenu menu)||menu.containerId!=request.menu())return false;boolean accepted=menu.button(player,request.revision(),request.action(),request.search());snapshot(player,menu);return accepted;}
    public static boolean process(ServerPlayer player,Request request){if(player==null||request.menu()<0||request.revision()<0||!(player.containerMenu instanceof SealLogisticsMenu menu)||menu.containerId!=request.menu())return false;boolean accepted=menu.request(player,request.revision(),request.slot(),request.amount());snapshot(player,menu);return accepted;}
    private static void send(ServerPlayer player,Object packet){if(registered&&player.connection!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void snapshot(ServerPlayer player,SealMenu menu){send(player,new MenuSnapshot(menu.containerId,menu.revision(),menu.category(),menu.seal().save()));}
    public static void snapshot(ServerPlayer player,SealLogisticsMenu menu){send(player,new LogisticsSnapshot(menu.containerId,menu.revision(),menu.start(),menu.end(),menu.entries()));}
    private static void world(ServerLevel level,List<CompoundTag> entries){for(ServerPlayer player:level.players())send(player,new WorldSnapshot(level.dimension().location(),false,entries));}
    public static void sync(ServerLevel level,SealData seal){world(level,List.of(seal.save()));}
    public static void remove(ServerLevel level,SealPos pos){CompoundTag tag=new CompoundTag();tag.putLong("pos",pos.pos().asLong());tag.putByte("face",(byte)pos.face().ordinal());tag.putBoolean("Removed",true);world(level,List.of(tag));}
    public static void syncAll(ServerPlayer player){
        send(player,new WorldSnapshot(player.serverLevel().dimension().location(),true,List.of()));
        List<CompoundTag> tags=SealService.get(player.serverLevel()).seals().stream().filter(s->player.serverLevel().hasChunkAt(s.position().pos())).map(SealData::save).toList();
        for(int i=0;i<tags.size();i+=1024)send(player,new WorldSnapshot(player.serverLevel().dimension().location(),false,tags.subList(i,Math.min(tags.size(),i+1024))));
    }
    public static void syncChunk(ServerPlayer player,net.minecraft.world.level.ChunkPos chunk){
        List<CompoundTag> tags=SealService.get(player.serverLevel()).seals().stream().filter(s->new net.minecraft.world.level.ChunkPos(s.position().pos()).equals(chunk)).map(SealData::save).toList();
        for(int i=0;i<tags.size();i+=1024)send(player,new WorldSnapshot(player.serverLevel().dimension().location(),false,tags.subList(i,Math.min(tags.size(),i+1024))));
    }
    public static void button(SealMenu menu,int button){CHANNEL.sendToServer(new Button(menu.containerId,menu.revision(),button));}
    public static void filter(SealMenu menu,int slot,int button,boolean quick){CHANNEL.sendToServer(new Filter(menu.containerId,menu.revision(),slot,button,quick));}
    public static void logistics(SealLogisticsMenu menu,int button,String search){CHANNEL.sendToServer(new Logistics(menu.containerId,menu.revision(),button,search));}
    public static void request(SealLogisticsMenu menu,int slot,int amount){CHANNEL.sendToServer(new Request(menu.containerId,menu.revision(),slot,amount));}
    public static void logisticsButton(SealLogisticsMenu menu,int action,String search){logistics(menu,action,search);}
    public static void logisticsRequest(SealLogisticsMenu menu,int slot,int amount){request(menu,slot,amount);}
    public static void ghost(SealMenu menu,int slot,int button,boolean quick){filter(menu,slot,button,quick);}
}
