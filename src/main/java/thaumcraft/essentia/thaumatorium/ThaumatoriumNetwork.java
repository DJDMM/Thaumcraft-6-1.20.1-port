package thaumcraft.essentia.thaumatorium;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.api.aspects.*;
import java.util.*;
import java.util.function.Supplier;

/** Resource IDs replace collision-prone original integer hashes; context and revision are server checked. */
public final class ThaumatoriumNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("thaumcraft","thaumatorium"),()->"1","1"::equals,"1"::equals);
    public record Select(int menu,int revision,ResourceLocation recipe) {}
    public record Snapshot(int menu,int revision,int capacity,List<ThaumatoriumMenu.RecipeView> recipes,AspectList stored) {}
    private ThaumatoriumNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(Select.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((packet,buffer)->{buffer.writeVarInt(packet.menu());buffer.writeVarInt(packet.revision());buffer.writeResourceLocation(packet.recipe());}).decoder(buffer->new Select(buffer.readVarInt(),buffer.readVarInt(),buffer.readResourceLocation())).consumerMainThread((packet,context)->{
            ServerPlayer player=context.get().getSender();if(player!=null&&player.containerMenu instanceof ThaumatoriumMenu menu&&menu.containerId==packet.menu()) {menu.select(player,packet.revision(),packet.recipe());menu.broadcastChanges();}context.get().setPacketHandled(true);
        }).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(ThaumatoriumNetwork::encode).decoder(ThaumatoriumNetwork::decode).consumerMainThread((packet,context)->{thaumcraft.essentia.thaumatorium.client.ThaumatoriumClientEvents.snapshot(packet);context.get().setPacketHandled(true);}).add();
    }
    public static void select(ThaumatoriumMenu menu,ResourceLocation id) {CHANNEL.sendToServer(new Select(menu.containerId,menu.revision(),id));}
    public static void snapshot(ServerPlayer player,ThaumatoriumMenu menu) {CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(menu.containerId,menu.revision(),menu.capacity(),menu.recipes(),menu.stored()));}
    private static void writeAspects(FriendlyByteBuf buffer,AspectList list) {buffer.writeVarInt(list.size());for(Aspect aspect:list.getAspects()) {buffer.writeUtf(aspect.getTag(),64);buffer.writeVarInt(list.getAmount(aspect));}}
    private static AspectList readAspects(FriendlyByteBuf buffer) {
        int count=buffer.readVarInt();if(count<0||count>64)throw new IllegalArgumentException("Aspect count");var list=new AspectList();
        for(int index=0;index<count;index++) {var aspect=Aspect.getAspect(buffer.readUtf(64));int amount=buffer.readVarInt();if(aspect==null||amount<=0||amount>500)throw new IllegalArgumentException("Aspect amount");list.add(aspect,amount);}return list;
    }
    private static void encode(Snapshot packet,FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.menu());buffer.writeVarInt(packet.revision());buffer.writeVarInt(packet.capacity());buffer.writeVarInt(packet.recipes().size());
        for(var recipe:packet.recipes()) {buffer.writeResourceLocation(recipe.id());buffer.writeItem(recipe.output());writeAspects(buffer,recipe.cost());buffer.writeBoolean(recipe.selected());}writeAspects(buffer,packet.stored());
    }
    private static Snapshot decode(FriendlyByteBuf buffer) {
        int menu=buffer.readVarInt(),revision=buffer.readVarInt(),capacity=buffer.readVarInt(),count=buffer.readVarInt();if(capacity<1||capacity>17||count<0||count>256)throw new IllegalArgumentException("Recipe count");
        List<ThaumatoriumMenu.RecipeView> recipes=new ArrayList<>();for(int index=0;index<count;index++)recipes.add(new ThaumatoriumMenu.RecipeView(buffer.readResourceLocation(),buffer.readItem(),readAspects(buffer),buffer.readBoolean()));return new Snapshot(menu,revision,capacity,List.copyOf(recipes),readAspects(buffer));
    }
}
