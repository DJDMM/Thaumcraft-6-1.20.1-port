package thaumcraft.golemancy.press;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

/** Design IDs are checked against server research and physical inventory; previews cannot pay. */
public final class GolemPressNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("thaumcraft","golem_press"),()->"1","1"::equals,"1"::equals);
    public record Request(int menu,int revision,long design,boolean start) {}
    public record Snapshot(int menu,int revision,long design,List<Boolean> owns) {}
    private GolemPressNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeLong(p.design());b.writeBoolean(p.start());})
                .decoder(b->new Request(b.readVarInt(),b.readVarInt(),b.readLong(),b.readBoolean())).consumerMainThread((p,c)->{var player=c.get().getSender();process(player,p);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeVarInt(p.menu());b.writeVarInt(p.revision());b.writeLong(p.design());b.writeVarInt(p.owns().size());for(boolean flag:p.owns())b.writeBoolean(flag);})
                .decoder(b->{int menu=b.readVarInt(),revision=b.readVarInt();long design=b.readLong();int count=b.readVarInt();if(count<0||count>32||revision<0)throw new IllegalArgumentException("Press snapshot bounds");List<Boolean> flags=new ArrayList<>();for(int i=0;i<count;i++)flags.add(b.readBoolean());return new Snapshot(menu,revision,design,List.copyOf(flags));})
                .consumerMainThread((p,c)->{thaumcraft.golemancy.press.client.GolemPressClientEvents.snapshot(p);c.get().setPacketHandled(true);}).add();
    }
    public static boolean process(ServerPlayer player,Request request) {
        if(player==null||request.menu()<0||request.revision()<0||!(player.containerMenu instanceof GolemPressMenu menu)||menu.containerId!=request.menu()||!menu.stillValid(player))return false;
        boolean accepted=request.start()?menu.startDesign(player,request.revision(),request.design()):menu.preview(player,request.revision(),request.design());
        menu.broadcastChanges();if(player.connection!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(menu.containerId,menu.revision(),menu.checkedDesignId(),menu.owns()));return accepted;
    }
    public static void request(GolemPressMenu menu,long design,boolean start) {CHANNEL.sendToServer(new Request(menu.containerId,menu.revision(),design,start));}
}
