package thaumcraft.auromancy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public final class FocusSelectionNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "focus_selection"), () -> "1", "1"::equals, "1"::equals);
    private FocusSelectionNetwork() {}
    public static void request(InteractionHand hand, int slot, ItemStack caster, ItemStack focus) {
        CHANNEL.sendToServer(new Change(hand, slot, caster.copy(), focus.copy()));
    }
    @Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            CHANNEL.registerMessage(0, Change.class, Change::encode, Change::decode, Change::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        }
    }
    public record Change(InteractionHand hand, int slot, ItemStack caster, ItemStack focus) {
        public Change { caster=caster.copy();focus=focus.copy(); }
        static void encode(Change p, FriendlyByteBuf buf) { buf.writeEnum(p.hand);buf.writeVarInt(p.slot);buf.writeItem(p.caster);buf.writeItem(p.focus); }
        static Change decode(FriendlyByteBuf buf) { return new Change(buf.readEnum(InteractionHand.class),buf.readVarInt(),buf.readItem(),buf.readItem()); }
        static void handle(Change p, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> FocusSelection.change(context.get().getSender(),p.hand,p.slot,p.caster,p.focus));
            context.get().setPacketHandled(true);
        }
    }
}
