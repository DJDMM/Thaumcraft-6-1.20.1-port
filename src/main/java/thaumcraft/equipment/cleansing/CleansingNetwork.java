package thaumcraft.equipment.cleansing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Optional;
import java.util.function.Supplier;

/** Only a server-to-client cosmetic completion packet. Vanilla hand-use packets control soap use. */
public final class CleansingNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "cleansing"), () -> "1", "1"::equals, "1"::equals);
    private static boolean registered;
    private CleansingNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, SoapFinished.class, SoapFinished::encode, SoapFinished::decode, SoapFinished::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void soapFinished(ServerPlayer player) {
        var sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "craftstart"));
        if (sound != null) player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, .25F, 1);
        if (player.connection != null) CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SoapFinished(player.serverLevel().dimension().location(), player.getX(),
                        player.getBoundingBox().minY, player.getZ(), player.getBbHeight()));
    }

    public record SoapFinished(ResourceLocation dimension, double x, double y, double z, float height) {
        public static void encode(SoapFinished packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension);
            buf.writeDouble(packet.x); buf.writeDouble(packet.y); buf.writeDouble(packet.z); buf.writeFloat(packet.height);
        }
        public static SoapFinished decode(FriendlyByteBuf buf) {
            return new SoapFinished(buf.readResourceLocation(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
        }
        public static void handle(SoapFinished packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> CleansingClient.soapFinished(packet)));
            context.get().setPacketHandled(true);
        }
    }
}
