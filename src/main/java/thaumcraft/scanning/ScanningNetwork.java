package thaumcraft.scanning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.AuromancyProgressionEvents;
import thaumcraft.scanning.client.ThaumometerClient;
import thaumcraft.world.aura.AuraManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Read-only server snapshots. There is deliberately no client discovery request. */
public final class ScanningNetwork {
    private static final int MAX_ASPECTS = 128;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "scanning"), () -> "1", "1"::equals, "1"::equals);

    private ScanningNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ScanResult.class, ScanResult::encode, ScanResult::decode, ScanResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static Snapshot capture(ServerPlayer player) {
        var hand = ThaumometerItem.heldHand(player);
        var level = player.serverLevel();
        Target target = null;
        if (ThaumometerItem.auraHand(player) == null || !player.isAlive() || player.isSpectator())
            return new Snapshot(level.dimension().location(), level.getGameTime(), 0, 0, 0, null);
        var scan = hand == null ? null : ThaumometerItem.findTarget(player, hand);
        if (scan != null) {
            Object scanned = ThaumometerItem.scannedObject(player, hand, scan);
            if (scan.aspects().size() > 0 || AuromancyProgressionEvents.scanFact(scanned) != null)
                target = target(scan, (scan.aspects().size() == 0 || KnowledgeStore.get(player).hasScanned(scan.key()))
                        && !KnowledgeStore.get(player).hasUnknownAspects(scan.aspects())
                        && !AuromancyProgressionEvents.hasUnseenScanFact(player, scanned));
        }
        return new Snapshot(level.dimension().location(), level.getGameTime(),
                AuraManager.getAuraBase(level, player.blockPosition()), AuraManager.getVis(level, player.blockPosition()),
                AuraManager.getFlux(level, player.blockPosition()), target);
    }

    public static void sendHud(ServerPlayer player) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), capture(player));
    }

    static void sendScan(ServerPlayer player, ThaumometerItem.ScanTarget scan, boolean discovered) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScanResult(player.serverLevel().dimension().location(), target(scan, true), discovered));
        sendHud(player);
    }

    private static Target target(ThaumometerItem.ScanTarget scan, boolean scanned) {
        List<AspectAmount> aspects = new ArrayList<>();
        for (Aspect aspect : scan.aspects().getAspects()) {
            int amount = scan.aspects().getAmount(aspect);
            if (amount > 0) aspects.add(new AspectAmount(aspect.getTag(), amount));
        }
        return new Target(scan.location(), aspects, scanned, scan.name());
    }

    public record AspectAmount(String tag, int amount) {
        public AspectAmount {
            if (tag == null || tag.length() > 64 || Aspect.getAspect(tag) == null || amount <= 0)
                throw new IllegalArgumentException("Invalid scanner aspect");
        }
    }

    public record Target(ThaumometerItem.TargetLocation location, List<AspectAmount> aspects, boolean scanned, Component name) {
        public Target {
            if (location == null || location.kind() == null || location.face() == null || location.position() == null
                    || !Double.isFinite(location.position().x) || !Double.isFinite(location.position().y)
                    || !Double.isFinite(location.position().z)
                    || (location.kind() == ThaumometerItem.TargetKind.BLOCK && location.blockPos() == null)
                    || (location.kind() == ThaumometerItem.TargetKind.ENTITY && location.entityId() < 0))
                throw new IllegalArgumentException("Invalid scanner location");
            aspects = List.copyOf(aspects);
            if (aspects.size() > MAX_ASPECTS || new HashSet<>(aspects.stream().map(AspectAmount::tag).toList()).size() != aspects.size())
                throw new IllegalArgumentException("Invalid scanner aspect list");
            name = name.copy();
        }

        public static void encode(Target packet, FriendlyByteBuf buf) {
            var location = packet.location;
            buf.writeEnum(location.kind());
            buf.writeVarInt(location.entityId());
            if (location.kind() == ThaumometerItem.TargetKind.BLOCK) buf.writeBlockPos(location.blockPos());
            buf.writeEnum(location.face());
            buf.writeDouble(location.position().x); buf.writeDouble(location.position().y); buf.writeDouble(location.position().z);
            buf.writeBoolean(packet.scanned);
            buf.writeComponent(packet.name);
            buf.writeVarInt(packet.aspects.size());
            for (AspectAmount aspect : packet.aspects) { buf.writeUtf(aspect.tag, 64); buf.writeVarInt(aspect.amount); }
        }

        public static Target decode(FriendlyByteBuf buf) {
            var kind = buf.readEnum(ThaumometerItem.TargetKind.class);
            int entity = buf.readVarInt();
            BlockPos block = kind == ThaumometerItem.TargetKind.BLOCK ? buf.readBlockPos() : null;
            Direction face = buf.readEnum(Direction.class);
            Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            boolean scanned = buf.readBoolean();
            Component name = buf.readComponent();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ASPECTS) throw new IllegalArgumentException("Invalid scanner aspect count");
            List<AspectAmount> aspects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) aspects.add(new AspectAmount(buf.readUtf(64), buf.readVarInt()));
            return new Target(new ThaumometerItem.TargetLocation(kind, entity, block, face, point), aspects, scanned, name);
        }
    }

    public record Snapshot(ResourceLocation dimension, long tick, int base, float vis, float flux, @Nullable Target target) {
        public Snapshot {
            if (dimension == null || base < 0 || !Float.isFinite(vis) || !Float.isFinite(flux) || vis < 0 || flux < 0)
                throw new IllegalArgumentException("Invalid scanner aura");
        }

        public static void encode(Snapshot packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension); buf.writeLong(packet.tick); buf.writeVarInt(packet.base);
            buf.writeFloat(packet.vis); buf.writeFloat(packet.flux);
            buf.writeBoolean(packet.target != null);
            if (packet.target != null) Target.encode(packet.target, buf);
        }

        public static Snapshot decode(FriendlyByteBuf buf) {
            return new Snapshot(buf.readResourceLocation(), buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean() ? Target.decode(buf) : null);
        }

        private static void handle(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ThaumometerClient.receive(packet)));
            context.get().setPacketHandled(true);
        }
    }

    public record ScanResult(ResourceLocation dimension, Target target, boolean discovered) {
        public static void encode(ScanResult packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension); Target.encode(packet.target, buf); buf.writeBoolean(packet.discovered);
        }
        public static ScanResult decode(FriendlyByteBuf buf) { return new ScanResult(buf.readResourceLocation(), Target.decode(buf), buf.readBoolean()); }
        private static void handle(ScanResult packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ThaumometerClient.receiveScan(packet)));
            context.get().setPacketHandled(true);
        }
    }
}
