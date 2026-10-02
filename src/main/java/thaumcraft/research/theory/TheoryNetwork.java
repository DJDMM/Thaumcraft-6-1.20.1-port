package thaumcraft.research.theory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.client.theory.TheoryClient;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class TheoryNetwork {
    public enum Action { START, DRAW, DRAW_BONUS, SELECT, FINISH, SCRAP }
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "theory"), () -> "2", "2"::equals, "2"::equals);
    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Request.class, Request::encode, Request::decode, Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void request(int menuId, long revision, Action action, int cardIndex, Set<String> aids) {
        CHANNEL.sendToServer(new Request(menuId, revision, action, cardIndex, Set.copyOf(aids)));
    }
    public static void send(ServerPlayer player, int menuId, CompoundTag state) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(menuId, state));
    }
    public static TheoryResult process(ResearchTableMenu menu, ServerPlayer player, Action action, long revision, int cardIndex, Set<String> aids) {
        if (player == null || player.containerMenu != menu || !menu.stillValid(player) || menu.table() == null || action == null) return TheoryResult.LOCKED;
        var table = menu.table();
        return switch (action) {
            case START -> table.start(player, revision, aids);
            case DRAW -> table.draw(player, revision, false);
            case DRAW_BONUS -> table.draw(player, revision, true);
            case SELECT -> table.select(player, revision, cardIndex);
            case FINISH -> table.finish(player, revision);
            case SCRAP -> table.scrap(player, revision);
        };
    }
    public record Snapshot(int menuId, CompoundTag state) {
        static void encode(Snapshot packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.menuId); buf.writeNbt(packet.state); }
        static Snapshot decode(FriendlyByteBuf buf) { int id = buf.readVarInt(); CompoundTag state = buf.readNbt(); return new Snapshot(id, state == null ? new CompoundTag() : state); }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TheoryClient.receive(packet.menuId, packet.state)));
            context.get().setPacketHandled(true);
        }
    }
    public record Request(int menuId, long revision, Action action, int cardIndex, Set<String> aids) {
        static void encode(Request packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.menuId); buf.writeLong(packet.revision); buf.writeEnum(packet.action); buf.writeVarInt(packet.cardIndex);
            buf.writeVarInt(packet.aids.size()); packet.aids.forEach(aid -> buf.writeUtf(aid, 64));
        }
        static Request decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt(); long revision = buf.readLong(); Action action = buf.readEnum(Action.class); int index = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > TheoryAids.keys().size()) throw new IllegalArgumentException("Invalid theory aid count");
            Set<String> aids = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) aids.add(buf.readUtf(64));
            return new Request(id, revision, action, index, Set.copyOf(aids));
        }
        static void handle(Request packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null || !(player.containerMenu instanceof ResearchTableMenu menu) || menu.containerId != packet.menuId || menu.table() == null) return;
                TheoryResult result = process(menu, player, packet.action, packet.revision, packet.cardIndex, packet.aids);
                send(player, menu.containerId, menu.table().clientSnapshot(player, result));
                menu.broadcastChanges();
            });
            context.get().setPacketHandled(true);
        }
    }
}
