package thaumcraft.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.client.research.ResearchClient;

import java.util.Optional;
import java.util.function.Supplier;

public final class ResearchNetwork {
    // Book read acknowledgments add a server-bound message. Peers must share the recipe and book formats.
    private static final String PROTOCOL = "4";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "research"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode,
                Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Discover.class, Discover::encode, Discover::decode,
                Discover::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Advance.class, Advance::encode, Advance::decode,
                Advance::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Read.class, Read::encode, Read::decode,
                Read::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void open(ServerPlayer player) { send(player, true, null); }
    public static void sync(ServerPlayer player) { send(player, false, null); }
    public static void requestDiscover(String key) { CHANNEL.sendToServer(new Discover(key)); }
    public static void requestAdvance(String key, int expectedStage) { CHANNEL.sendToServer(new Advance(key, expectedStage)); }
    public static void requestRead(String key, int expectedStage, int addendumMask) {
        CHANNEL.sendToServer(new Read(key, expectedStage, addendumMask));
    }

    public static boolean processRead(ServerPlayer player, String key, int expectedStage, int addendumMask) {
        return player != null && holdsBook(player)
                && KnowledgeStore.of(player.serverLevel()).recordBookRead(player.getUUID(), key, expectedStage, addendumMask);
    }

    private static boolean holdsBook(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(ResearchModule.THAUMONOMICON.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(ResearchModule.THAUMONOMICON.get());
    }
    static ResearchProgression.Result processAdvance(ServerPlayer player, String key, int expectedStage) {
        return holdsBook(player) ? ResearchProgression.advance(player, key, expectedStage) : ResearchProgression.Result.NO_BOOK;
    }
    static ResearchProgression.Result processDiscover(ServerPlayer player, String key) {
        if (!holdsBook(player)) return ResearchProgression.Result.NO_BOOK;
        if (!ResearchProgression.legacyLessonAvailable(KnowledgeStore.get(player), key)) return ResearchProgression.Result.UNSUPPORTED;
        return KnowledgeStore.discoverResearch(player, key) ? ResearchProgression.Result.COMPLETE : ResearchProgression.Result.MISSING_REQUIREMENTS;
    }
    private static void send(ServerPlayer player, boolean open, ResearchProgression.Result result) {
        if (player.connection == null) return; // Offline GameTest players have no network session.
        CompoundTag state = KnowledgeStore.get(player).save();
        state.putInt("ScanCount", KnowledgeStore.get(player).scanCount());
        state.remove("Scans");
        state.remove("CreditedScans");
        // Crucible recipes use a server reload listener instead of RecipeManager's vanilla sync.
        // Send detached, read-only previews; these never replace server-side payment checks.
        ListTag recipes = new ListTag();
        for (var entry : thaumcraft.alchemy.CrucibleRecipes.all()) {
            CompoundTag preview = new CompoundTag();
            preview.putString("Id", entry.id().toString());
            preview.putString("Research", entry.research());
            preview.putString("Catalyst", entry.catalyst().toJson().toString());
            preview.put("Output", entry.output().save(new CompoundTag()));
            entry.cost().writeToNBT(preview);
            recipes.add(preview);
        }
        state.put("CrucibleRecipePreviews", recipes);
        if (result != null) state.putString("ProgressResult", result.name());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(state, open));
    }

    public record Snapshot(CompoundTag state, boolean open) {
        static void encode(Snapshot packet, FriendlyByteBuf buf) { buf.writeNbt(packet.state); buf.writeBoolean(packet.open); }
        static Snapshot decode(FriendlyByteBuf buf) {
            CompoundTag state = buf.readNbt();
            return new Snapshot(state == null ? new CompoundTag() : state, buf.readBoolean());
        }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ResearchClient.receive(packet.state, packet.open)));
            context.get().setPacketHandled(true);
        }
    }
    public record Discover(String key) {
        static void encode(Discover packet, FriendlyByteBuf buf) { buf.writeUtf(packet.key, 128); }
        static Discover decode(FriendlyByteBuf buf) { return new Discover(buf.readUtf(128)); }
        static void handle(Discover packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null) return;
                send(player, false, processDiscover(player, packet.key));
            });
            context.get().setPacketHandled(true);
        }
    }
    public record Advance(String key, int expectedStage) {
        static void encode(Advance packet, FriendlyByteBuf buf) { buf.writeUtf(packet.key, 128); buf.writeVarInt(packet.expectedStage); }
        static Advance decode(FriendlyByteBuf buf) { return new Advance(buf.readUtf(128), buf.readVarInt()); }
        static void handle(Advance packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null) return;
                send(player, false, processAdvance(player, packet.key, packet.expectedStage));
            });
            context.get().setPacketHandled(true);
        }
    }
    public record Read(String key, int expectedStage, int addendumMask) {
        static void encode(Read packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.key, 128); buf.writeVarInt(packet.expectedStage); buf.writeInt(packet.addendumMask);
        }
        static Read decode(FriendlyByteBuf buf) { return new Read(buf.readUtf(128), buf.readVarInt(), buf.readInt()); }
        static void handle(Read packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null) return;
                processRead(player, packet.key, packet.expectedStage, packet.addendumMask);
                send(player, false, null);
            });
            context.get().setPacketHandled(true);
        }
    }
}

