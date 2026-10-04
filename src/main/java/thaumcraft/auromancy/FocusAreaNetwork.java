package thaumcraft.auromancy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;
/** Original G/Ctrl-G commands carry only a bounded action; the server selects the physical caster. */
public final class FocusAreaNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("thaumcraft","focus_area"),()->"1","1"::equals,"1"::equals);
    private FocusAreaNetwork(){}
    public static void request(int mode){CHANNEL.sendToServer(new Cycle(mode));}
    @Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class AreaRegistration {
        @SubscribeEvent public static void setupArea(FMLCommonSetupEvent event){CHANNEL.registerMessage(0,Cycle.class,Cycle::encode,Cycle::decode,Cycle::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));}
    }
    private record Cycle(int mode){
        static void encode(Cycle p,FriendlyByteBuf buf){buf.writeByte(p.mode);}
        static Cycle decode(FriendlyByteBuf buf){return new Cycle(buf.readUnsignedByte());}
        static void handle(Cycle p,Supplier<NetworkEvent.Context> context){context.get().enqueueWork(()->thaumcraft.auromancy.media.FocusPlanArea.cycle(context.get().getSender(),p.mode));context.get().setPacketHandled(true);}
    }
}
