package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.auromancy.table.client.FocalManipulatorClient;

import java.util.Optional;
import java.util.function.Supplier;

/** Coordinates identify the expected live menu; they never grant remote access to a table. */
public final class FocalManipulatorNetwork {
    public enum Action { EDIT, START }
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "focal_manipulator"), () -> "1", "1"::equals, "1"::equals);
    private FocalManipulatorNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Request.class, Request::encode, Request::decode, Request::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void request(int id, BlockPos position, long revision, Action action, CompoundTag graph, String name) {
        CHANNEL.sendToServer(new Request(id, position, revision, action, graph.copy(), name));
    }
    public static void send(ServerPlayer player, int id, CompoundTag state) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(id, state.copy()));
    }
    public static FocalManipulatorResult process(FocalManipulatorMenu menu, ServerPlayer player, BlockPos pos, long revision, Action action, CompoundTag graph, String name) {
        if (menu == null || player == null || player.containerMenu != menu || menu.table() == null || !menu.position().equals(pos)
                || !menu.stillValid(player) || action == null) return FocalManipulatorResult.LOCKED;
        return switch (action) {
            case EDIT -> menu.table().edit(player, revision, graph, name);
            case START -> menu.table().start(player, revision);
        };
    }
    public record Snapshot(int id, CompoundTag state) {
        static void encode(Snapshot packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.id); buffer.writeNbt(packet.state); }
        static Snapshot decode(FriendlyByteBuf buffer) { int id = buffer.readVarInt(); CompoundTag tag = buffer.readNbt(); return new Snapshot(id, tag == null ? new CompoundTag() : tag); }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FocalManipulatorClient.receive(packet.id, packet.state)));
            ctx.get().setPacketHandled(true);
        }
    }
    public record Request(int id, BlockPos position, long revision, Action action, CompoundTag graph, String name) {
        public Request { position = position.immutable(); graph = graph.copy(); }
        static void encode(Request packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.id); buffer.writeBlockPos(packet.position); buffer.writeLong(packet.revision);
            buffer.writeEnum(packet.action); buffer.writeNbt(packet.graph); buffer.writeUtf(packet.name, 100);
        }
        static Request decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt(); BlockPos pos = buffer.readBlockPos(); long revision = buffer.readLong();
            Action action = buffer.readEnum(Action.class); CompoundTag graph = buffer.readNbt();
            return new Request(id, pos, revision, action, graph == null ? new CompoundTag() : graph, buffer.readUtf(100));
        }
        static void handle(Request packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !(player.containerMenu instanceof FocalManipulatorMenu menu) || menu.containerId != packet.id) return;
                var result = process(menu, player, packet.position, packet.revision, packet.action, packet.graph, packet.name);
                if (menu.table() != null) send(player, menu.containerId, menu.table().clientSnapshot(player, result));
                menu.broadcastChanges();
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
