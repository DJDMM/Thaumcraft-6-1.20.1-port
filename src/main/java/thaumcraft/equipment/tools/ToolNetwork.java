package thaumcraft.equipment.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

/** Sounding is server-to-client only. The sole client request cycles the currently held shovel's mode. */
public final class ToolNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "tools"), () -> "1", "1"::equals, "1"::equals);
    private ToolNetwork() {}
    public static void sendSounding(ServerPlayer player, BlockPos pos, int rank) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sounding(player.level().dimension().location(), pos, rank));
    }
    public static void cycleMode() { CHANNEL.sendToServer(new CycleMode()); }

    @Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            CHANNEL.registerMessage(0, Sounding.class, Sounding::encode, Sounding::decode, Sounding::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            CHANNEL.registerMessage(1, CycleMode.class, CycleMode::encode, CycleMode::decode, CycleMode::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        }
    }
    public record Sounding(ResourceLocation dimension, BlockPos pos, int rank) {
        public Sounding { if (rank < 1 || rank > 4) throw new IllegalArgumentException("Invalid sounding rank"); pos = pos.immutable(); }
        static void encode(Sounding packet, FriendlyByteBuf buffer) { buffer.writeResourceLocation(packet.dimension); buffer.writeBlockPos(packet.pos); buffer.writeByte(packet.rank); }
        static Sounding decode(FriendlyByteBuf buffer) { return new Sounding(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readUnsignedByte()); }
        static void handle(Sounding packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ToolClientEvents.sounding(packet)));
            context.get().setPacketHandled(true);
        }
    }
    public record CycleMode() {
        static void encode(CycleMode packet, FriendlyByteBuf buffer) {}
        static CycleMode decode(FriendlyByteBuf buffer) { return new CycleMode(); }
        static void handle(CycleMode packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.isAlive() || player.isSpectator()
                        || !(player.getMainHandItem().getItem() instanceof ElementalShovelItem)) return;
                ElementalShovelItem.cycleOrientation(player.getMainHandItem());
                player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
            });
            context.get().setPacketHandled(true);
        }
    }
}
