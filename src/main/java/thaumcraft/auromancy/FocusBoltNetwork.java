package thaumcraft.auromancy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.auromancy.client.FocusBoltClient;
import java.util.Optional;
import java.util.function.Supplier;

/** Visual packet only: clients cannot submit a target, cast, or debit. */
public final class FocusBoltNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft","focus_bolt"),()->"1","1"::equals,"1"::equals);
    private FocusBoltNetwork() {}
    @Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class BoltRegistration {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            CHANNEL.registerMessage(0,Zap.class,Zap::encode,Zap::decode,Zap::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }
    public static boolean valid(Vec3 start,Vec3 end,float width) {
        return start!=null&&end!=null&&finite(start)&&finite(end)&&start.distanceToSqr(end)<=32*32
                &&Float.isFinite(width)&&width>0&&width<=4;
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
    public static void send(ServerLevel level,Vec3 start,Vec3 end,int color,float width) {
        if (!level.getServer().isSameThread()||!valid(start,end,width)) return;
        var packet=new Zap(level.dimension().location(),start,end,color,width);
        for(var player:level.players())if(player.connection!=null&&player.distanceToSqr(start)<=64*64)
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
    }
    public record Zap(ResourceLocation dimension,Vec3 source,Vec3 target,int color,float width) {
        static void encode(Zap p,FriendlyByteBuf b) {
            b.writeResourceLocation(p.dimension);write(b,p.source);write(b,p.target);b.writeInt(p.color);b.writeFloat(p.width);
        }
        private static void write(FriendlyByteBuf b,Vec3 v) { b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z); }
        private static Vec3 read(FriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
        static Zap decode(FriendlyByteBuf b) { return new Zap(b.readResourceLocation(),read(b),read(b),b.readInt(),b.readFloat()); }
        static void handle(Zap p,Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->FocusBoltClient.receive(p)));
            c.get().setPacketHandled(true);
        }
    }
}
