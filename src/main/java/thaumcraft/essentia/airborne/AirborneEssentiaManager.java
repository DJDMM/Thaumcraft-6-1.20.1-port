package thaumcraft.essentia.airborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.essentia.transport.TubeBlockEntity;
import thaumcraft.essentia.transport.TubeBufferBlockEntity;
import thaumcraft.essentia.transport.TubeFilterBlockEntity;
import thaumcraft.essentia.centrifuge.CentrifugeBlockEntity;
import thaumcraft.essentia.thaumatorium.ThaumatoriumBlockEntity;
import thaumcraft.essentia.thaumatorium.ThaumatoriumTopBlockEntity;
import thaumcraft.golemancy.press.GolemPressBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * BETA26's airborne source access, independent of tube suction and connectivity.
 * Shared discovery and retry rules serve the matrix and both transfusers.
 * Mirrors and cross-dimension transfer remain unsupported.
 */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AirborneEssentiaManager {
    public static final long RETRY_DELAY_MILLIS = 10_000L;
    /** Largest range among the original matrix, transfuser and mirror consumers. */
    public static final int MAX_RANGE = 16;
    private static final SourceCache RUNTIME = new SourceCache(System::currentTimeMillis);

    private AirborneEssentiaManager() {}

    /**
     * Debit exactly one stored unit, returning the original matrix trail endpoints.
     * The caller pays its recipe and sends FX only if this result is present.
     * The target is consumer.below(), matching the altar beneath the matrix.
     */
    public static Optional<Transfer> drain(BlockEntity consumer, Aspect aspect,
                                           @Nullable Direction direction, int range, int ext) {
        return RUNTIME.drain(consumer, aspect, direction, range, ext);
    }

    /** Insert one supplied unit, preferring nonempty compatible sources before empty ones. */
    public static Optional<Transfer> add(BlockEntity consumer, Aspect aspect,
                                         @Nullable Direction direction, int range, int ext) {
        return RUNTIME.add(consumer, aspect, direction, range, ext);
    }

    /** Read-only per-call confirmation; only its owner can commit it, once. */
    public static Optional<DrainConfirmation> prepareDrain(BlockEntity consumer, Aspect aspect,
                                                           @Nullable Direction direction, int range, int ext) {
        return RUNTIME.prepareDrain(consumer, aspect, direction, range, ext);
    }

    /** Debit a previewed source, never a global last queried source. */
    public static Optional<Transfer> confirmDrain(BlockEntity consumer, DrainConfirmation confirmation) {
        return RUNTIME.confirmDrain(consumer, confirmation);
    }

    /** Physical tube source -> airborne jar. Both native operations complete as one guarded unit. */
    public static Optional<Transfer> transferToSources(BlockEntity consumer, BlockEntity source,
            Aspect aspect, Direction sourceFace, @Nullable Direction direction, int range, int ext) {
        return RUNTIME.transferToSources(consumer, source, aspect, sourceFace, direction, range, ext);
    }

    /** Airborne jar -> physical tube destination. Refusal restores the exact source state. */
    public static Optional<Transfer> transferFromSources(BlockEntity consumer, BlockEntity destination,
            Aspect aspect, Direction destinationFace, @Nullable Direction direction, int range, int ext) {
        return RUNTIME.transferFromSources(consumer, destination, aspect, destinationFace, direction, range, ext);
    }

    /** Original ess_input bridge: its rear peer pays into the forward airborne region. */
    public static Optional<Transfer> transferFromTransport(BlockEntity consumer, BlockEntity peer,
                                                            Direction front, int range, int ext) {
        ServerLevel level = validConsumer(consumer, Aspect.AIR, range);
        if (RUNTIME.operationActive || front == null || !nativeTransport(peer) || !validEndpoint(level, peer))
            return Optional.empty();
        IEssentiaTransport transport = (IEssentiaTransport) peer;
        if (!transport.isConnectable(front) || !transport.canOutputTo(front)
                || transport.getEssentiaAmount(front) <= 0 || transport.getSuctionAmount(front) >= 128
                || transport.getMinimumSuction() > 128) return Optional.empty();
        return RUNTIME.transferToSources(consumer, peer, transport.getEssentiaType(front), front, front, range, ext);
    }

    /** Original ess_output bridge: a typed suction peer receives from the forward region. */
    public static Optional<Transfer> transferToTransport(BlockEntity consumer, BlockEntity peer,
                                                          Direction front, int range, int ext) {
        ServerLevel level = validConsumer(consumer, Aspect.AIR, range);
        if (RUNTIME.operationActive || front == null || !nativeTransport(peer) || !validEndpoint(level, peer))
            return Optional.empty();
        IEssentiaTransport transport = (IEssentiaTransport) peer;
        if (!transport.isConnectable(front) || !transport.canInputFrom(front)
                || transport.getSuctionAmount(front) <= 0) return Optional.empty();
        return RUNTIME.transferFromSources(consumer, peer, transport.getSuctionType(front), front, front, range, ext);
    }

    /** Commit an already previewed unit into an actual native transport endpoint. */
    public static Optional<Transfer> confirmDrainTo(BlockEntity consumer, DrainConfirmation confirmation,
                                                    BlockEntity destination, Direction destinationFace) {
        return RUNTIME.confirmDrainTo(consumer, confirmation, destination, destinationFace);
    }

    /** An opaque transient promise, not an essentia buffer or persistent reservation. */
    public static final class DrainConfirmation {
        private final SourceCache owner;
        private final ServerLevel level;
        private final BlockEntity consumer, source;
        private final Aspect aspect;
        private final int ext;
        private final long lifecycleEpoch;
        private boolean used;

        private DrainConfirmation(SourceCache owner, ServerLevel level, BlockEntity consumer,
                                   BlockEntity source, Aspect aspect, int ext) {
            this.owner = owner; this.level = level; this.consumer = consumer;
            this.source = source; this.aspect = aspect; this.ext = ext; this.lifecycleEpoch = owner.lifecycleEpoch;
        }

        public BlockPos source() { return source.getBlockPos().immutable(); }
        public BlockPos target() { return consumer.getBlockPos().immutable(); }
        public Aspect aspect() { return aspect; }
        public int ext() { return ext; }
    }

    /** A read-only source query; failure does not invalidate a positive cache. */
    public static boolean find(BlockEntity consumer, Aspect aspect, @Nullable Direction direction, int range) {
        return RUNTIME.find(consumer, aspect, direction, range);
    }

    /** BETA26 clears the discovered sources here, but deliberately keeps the retry delay. */
    public static void refreshSources(BlockEntity consumer) { RUNTIME.refreshSources(consumer); }

    /** Explicit lifecycle adaptation: forget both lists and delay when a consumer is removed. */
    public static void forgetConsumer(BlockEntity consumer) { RUNTIME.forgetConsumer(consumer); }

    public record Transfer(BlockPos source, BlockPos target, Aspect aspect, int ext) {
        public Transfer {
            source = Objects.requireNonNull(source).immutable();
            target = Objects.requireNonNull(target).immutable();
            Objects.requireNonNull(aspect);
        }
    }

    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) RUNTIME.forgetLevel(level);
    }

    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        RUNTIME.forgetServer(event.getServer());
    }

    /**
     * Instance-scoped clock injection keeps deterministic tests out of the runtime cache.
     * Cache keys are the actual level identity and consumer position, never aspect,
     * direction or range. A successful list has no TTL. Only coordinates are retained.
     */
    static final class SourceCache {
        private final LongSupplier clock;
        private boolean operationActive;
        private long lifecycleEpoch;
        private final Map<ServerLevel, LevelSources> levels = new IdentityHashMap<>();

        SourceCache(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

        synchronized Optional<Transfer> drain(BlockEntity consumer, Aspect aspect,
                                                @Nullable Direction direction, int range, int ext) {
            if (operationActive) return Optional.empty();
            operationActive = true;
            try { return drainUnlocked(consumer, aspect, direction, range, ext); }
            finally { operationActive = false; }
        }

        private Optional<Transfer> drainUnlocked(BlockEntity consumer, Aspect aspect,
                                                @Nullable Direction direction, int range, int ext) {
            ServerLevel level = validConsumer(consumer, aspect, range);
            if (level == null) return Optional.empty();
            BlockPos origin = consumer.getBlockPos();
            LevelSources state = levels.computeIfAbsent(level, ignored -> new LevelSources());
            List<BlockPos> sources = sources(level, origin, direction, range, state);
            if (sources == null) return Optional.empty();
            for (BlockPos pos : sources) {
                BlockEntity sourceTile = loadedBlockEntity(level, pos);
                // BETA26 breaks here: a stale earlier source prevents later sources this time.
                if (!(sourceTile instanceof IAspectSource source)) break;
                if (source.isBlocked()) continue;
                // A jar's label governs insertion. Airborne drain uses its actual contents.
                if (source.takeFromContainer(aspect, 1))
                    return Optional.of(new Transfer(pos, origin.below(), aspect, ext));
            }
            state.sources.remove(origin);
            state.delays.put(origin.immutable(), clock.getAsLong() + RETRY_DELAY_MILLIS);
            return Optional.empty();
        }

        synchronized boolean find(BlockEntity consumer, Aspect aspect, @Nullable Direction direction, int range) {
            if (operationActive) return false;
            ServerLevel level = validConsumer(consumer, aspect, range);
            if (level == null) return false;
            BlockPos origin = consumer.getBlockPos();
            LevelSources state = levels.computeIfAbsent(level, ignored -> new LevelSources());
            List<BlockPos> sources = sources(level, origin, direction, range, state);
            if (sources == null) return false;
            for (BlockPos pos : sources) {
                BlockEntity sourceTile = loadedBlockEntity(level, pos);
                if (!(sourceTile instanceof IAspectSource source)) break;
                if (!source.isBlocked() && source.doesContainerContainAmount(aspect, 1)) return true;
            }
            return false;
        }

        synchronized Optional<Transfer> add(BlockEntity consumer, Aspect aspect,
                                             @Nullable Direction direction, int range, int ext) {
            if (operationActive) return Optional.empty();
            operationActive = true;
            try {
                ServerLevel level = validConsumer(consumer, aspect, range);
                if (level == null) return Optional.empty();
                return addUnlocked(level, consumer, aspect, direction, range, ext, null);
            } finally { operationActive = false; }
        }

        synchronized Optional<DrainConfirmation> prepareDrain(BlockEntity consumer, Aspect aspect,
                @Nullable Direction direction, int range, int ext) {
            if (operationActive) return Optional.empty();
            operationActive = true;
            try { return prepareDrainUnlocked(consumer, aspect, direction, range, ext); }
            finally { operationActive = false; }
        }

        private Optional<DrainConfirmation> prepareDrainUnlocked(BlockEntity consumer, Aspect aspect,
                @Nullable Direction direction, int range, int ext) {
            ServerLevel level = validConsumer(consumer, aspect, range);
            if (level == null) return Optional.empty();
            BlockPos origin = consumer.getBlockPos();
            LevelSources state = levels.computeIfAbsent(level, ignored -> new LevelSources());
            List<BlockPos> sources = sources(level, origin, direction, range, state);
            if (sources == null) return Optional.empty();
            for (BlockPos pos : sources) {
                BlockEntity sourceTile = loadedBlockEntity(level, pos);
                if (!(sourceTile instanceof IAspectSource source)) break;
                if (!source.isBlocked() && source.doesContainerContainAmount(aspect, 1)
                        && validEndpoint(level, sourceTile) && validEndpoint(level, consumer))
                    return Optional.of(new DrainConfirmation(this, level, consumer, sourceTile, aspect, ext));
            }
            failed(state, origin);
            return Optional.empty();
        }

        synchronized Optional<Transfer> confirmDrain(BlockEntity consumer, DrainConfirmation confirmation) {
            if (operationActive || !ownedConfirmation(consumer, confirmation)) return Optional.empty();
            operationActive = true;
            try {
                confirmation.used = true;
                if (!liveConfirmation(confirmation)) return Optional.empty();
                IAspectSource source = (IAspectSource) confirmation.source;
                return source.takeFromContainer(confirmation.aspect, 1)
                        ? Optional.of(confirmedTransfer(confirmation)) : Optional.empty();
            } finally { operationActive = false; }
        }

        synchronized Optional<Transfer> transferToSources(BlockEntity consumer, BlockEntity source,
                Aspect aspect, Direction sourceFace, @Nullable Direction direction, int range, int ext) {
            if (operationActive) return Optional.empty();
            operationActive = true;
            try {
                ServerLevel level = validConsumer(consumer, aspect, range);
                if (level == null || !nativeTransport(source) || !validEndpoint(level, source)
                        || !adjacent(consumer, source, sourceFace)) return Optional.empty();
                IEssentiaTransport transport = (IEssentiaTransport) source;
                if (!transport.isConnectable(sourceFace) || !transport.canOutputTo(sourceFace)
                        || transport.getEssentiaAmount(sourceFace) <= 0) return Optional.empty();
                // Selection is still the handler's two-pass order. Payment happens only after
                // a real native jar can take this unit, never through an insertion preview.
                return addUnlocked(level, consumer, aspect, direction, range, ext,
                        new TransportDebit(source, transport, sourceFace));
            } finally { operationActive = false; }
        }

        synchronized Optional<Transfer> transferFromSources(BlockEntity consumer, BlockEntity destination,
                Aspect aspect, Direction destinationFace, @Nullable Direction direction, int range, int ext) {
            if (operationActive) return Optional.empty();
            operationActive = true;
            try {
                ServerLevel level = validConsumer(consumer, aspect, range);
                if (!validDestination(level, consumer, destination, destinationFace)) return Optional.empty();
                Optional<DrainConfirmation> preview = prepareDrainUnlocked(consumer, aspect, direction, range, ext);
                return preview.isPresent()
                        ? confirmDrainToUnlocked(consumer, preview.orElseThrow(), destination, destinationFace)
                        : Optional.empty();
            } finally { operationActive = false; }
        }

        synchronized Optional<Transfer> confirmDrainTo(BlockEntity consumer, DrainConfirmation confirmation,
                BlockEntity destination, Direction destinationFace) {
            if (operationActive || !ownedConfirmation(consumer, confirmation)) return Optional.empty();
            operationActive = true;
            try { return confirmDrainToUnlocked(consumer, confirmation, destination, destinationFace); }
            finally { operationActive = false; }
        }

        private Optional<Transfer> confirmDrainToUnlocked(BlockEntity consumer, DrainConfirmation confirmation,
                BlockEntity destination, Direction destinationFace) {
            if (!ownedConfirmation(consumer, confirmation)) return Optional.empty();
            confirmation.used = true;
            // The generic IAspectSource API remains usable for direct drain/add/preview.
            // Atomic two-endpoint transfer is deliberately limited to audited native jars
            // and transports: arbitrary addon callbacks have no rollback contract.
            if (!liveConfirmation(confirmation) || !(confirmation.source instanceof EssentiaJarBlockEntity source)
                    || !validDestination(confirmation.level, consumer, destination, destinationFace)) return Optional.empty();
            CompoundTag before = source.saveWithoutMetadata();
            if (!source.takeFromContainer(confirmation.aspect, 1)) return Optional.empty();
            if (!validEndpoint(confirmation.level, consumer) || !validEndpoint(confirmation.level, destination)
                    || !validEndpoint(confirmation.level, source)) {
                restore(confirmation.level, source, before);
                return Optional.empty();
            }
            int accepted;
            try { accepted = ((IEssentiaTransport) destination).addEssentia(confirmation.aspect, 1, destinationFace); }
            catch (RuntimeException exception) { restore(confirmation.level, source, before); throw exception; }
            if (accepted != 1) {
                restore(confirmation.level, source, before);
                return Optional.empty();
            }
            return Optional.of(confirmedTransfer(confirmation));
        }

        private boolean ownedConfirmation(BlockEntity consumer, DrainConfirmation confirmation) {
            return confirmation != null && confirmation.owner == this && confirmation.consumer == consumer
                    && !confirmation.used && confirmation.lifecycleEpoch == lifecycleEpoch
                    && ownedLevel(consumer) == confirmation.level;
        }

        private boolean liveConfirmation(DrainConfirmation confirmation) {
            return validEndpoint(confirmation.level, confirmation.consumer)
                    && validEndpoint(confirmation.level, confirmation.source)
                    && confirmation.source instanceof IAspectSource source && !source.isBlocked()
                    && source.doesContainerContainAmount(confirmation.aspect, 1);
        }

        private static Transfer confirmedTransfer(DrainConfirmation confirmation) {
            return new Transfer(confirmation.source.getBlockPos(), confirmation.consumer.getBlockPos(),
                    confirmation.aspect, confirmation.ext);
        }

        private Optional<Transfer> addUnlocked(ServerLevel level, BlockEntity consumer, Aspect aspect,
                @Nullable Direction direction, int range, int ext, @Nullable TransportDebit debit) {
            BlockPos origin = consumer.getBlockPos();
            LevelSources state = levels.computeIfAbsent(level, ignored -> new LevelSources());
            List<BlockPos> sources = sources(level, origin, direction, range, state);
            if (sources == null) return Optional.empty();
            List<BlockEntity> empties = new ArrayList<>();
            for (BlockPos pos : sources) {
                BlockEntity tile = loadedBlockEntity(level, pos);
                if (!(tile instanceof IAspectSource destination)) break;
                if (destination.isBlocked()) continue;
                if (destination.doesContainerAccept(aspect)
                        && (destination.getAspects() == null || destination.getAspects().visSize() == 0)) {
                    empties.add(tile);
                } else if (destination.doesContainerAccept(aspect)) {
                    if (insert(level, consumer, tile, destination, aspect, debit))
                        return Optional.of(new Transfer(origin, pos, aspect, ext));
                    if (debit != null && debit.aborted) return Optional.empty();
                }
            }
            for (BlockEntity discovered : empties) {
                BlockEntity tile = loadedBlockEntity(level, discovered.getBlockPos());
                if (!(tile instanceof IAspectSource destination)) break;
                // Original pass two omits another brace check. The modern port rechecks it
                // and the tile identity because a callback may change a deferred endpoint.
                if (tile != discovered || destination.isBlocked()) continue;
                if (destination.doesContainerAccept(aspect)) {
                    if (insert(level, consumer, tile, destination, aspect, debit))
                        return Optional.of(new Transfer(origin, tile.getBlockPos(), aspect, ext));
                    if (debit != null && debit.aborted) return Optional.empty();
                }
            }
            failed(state, origin);
            return Optional.empty();
        }

        private static boolean insert(ServerLevel level, BlockEntity consumer, BlockEntity tile,
                IAspectSource destination, Aspect aspect, @Nullable TransportDebit debit) {
            if (!validEndpoint(level, consumer) || !validEndpoint(level, tile)) return false;
            if (debit == null) return destination.addToContainer(aspect, 1) <= 0;
            if (!(destination instanceof EssentiaJarBlockEntity jar) || !validEndpoint(level, debit.tile)
                    || !jar.doesContainerAccept(aspect) || jar.amount() > 0 && jar.aspect() != aspect
                    || !jar.isVoid() && jar.amount() >= EssentiaJarBlockEntity.CAPACITY) return false;
            CompoundTag before = debit.tile.saveWithoutMetadata();
            if (debit.transport.takeEssentia(aspect, 1, debit.face) != 1) {
                debit.aborted = true; return false;
            }
            if (!validEndpoint(level, consumer) || !validEndpoint(level, tile) || !validEndpoint(level, debit.tile)
                    || jar.isBlocked() || !jar.doesContainerAccept(aspect)) {
                debit.aborted = true; restore(level, debit.tile, before); return false;
            }
            int remainder;
            try { remainder = jar.addToContainer(aspect, 1); }
            catch (RuntimeException exception) { restore(level, debit.tile, before); throw exception; }
            if (remainder <= 0) return true;
            debit.aborted = true;
            restore(level, debit.tile, before);
            return false;
        }

        private void failed(LevelSources state, BlockPos origin) {
            state.sources.remove(origin);
            state.delays.put(origin.immutable(), clock.getAsLong() + RETRY_DELAY_MILLIS);
        }

        private static final class TransportDebit {
            private final BlockEntity tile;
            private final IEssentiaTransport transport;
            private final Direction face;
            private boolean aborted;
            private TransportDebit(BlockEntity tile, IEssentiaTransport transport, Direction face) {
                this.tile = tile; this.transport = transport; this.face = face;
            }
        }

        synchronized void refreshSources(BlockEntity consumer) {
            ServerLevel level = ownedLevel(consumer);
            if (level != null && levels.containsKey(level)) levels.get(level).sources.remove(consumer.getBlockPos());
        }

        synchronized void forgetConsumer(BlockEntity consumer) {
            ServerLevel level = ownedLevel(consumer);
            if (level == null) return;
            ++lifecycleEpoch;
            LevelSources state = levels.get(level);
            if (state == null) return;
            state.sources.remove(consumer.getBlockPos());
            state.delays.remove(consumer.getBlockPos());
            if (state.sources.isEmpty() && state.delays.isEmpty()) levels.remove(level);
        }

        synchronized void forgetLevel(ServerLevel level) { ++lifecycleEpoch; levels.remove(level); }

        synchronized void forgetServer(MinecraftServer server) {
            ++lifecycleEpoch;
            levels.keySet().removeIf(level -> level.getServer() == server);
        }

        private @Nullable List<BlockPos> sources(ServerLevel level, BlockPos origin,
                                                 @Nullable Direction direction, int range, LevelSources state) {
            List<BlockPos> cached = state.sources.get(origin);
            if (cached != null) return cached;
            Long deadline = state.delays.get(origin);
            if (deadline != null) {
                if (deadline > clock.getAsLong()) return null;
                state.delays.remove(origin);
            }
            List<BlockPos> found = new ArrayList<>();
            forEachCandidate(origin, direction, range, pos -> {
                if (loadedBlockEntity(level, pos) instanceof IAspectSource) found.add(pos);
            });
            if (found.isEmpty()) {
                state.delays.put(origin.immutable(), clock.getAsLong() + RETRY_DELAY_MILLIS);
                return null;
            }
            // List.sort is stable: equal distances keep the original aa/bb/cc traversal order.
            found.sort(Comparator.comparingDouble(origin::distSqr));
            List<BlockPos> result = List.copyOf(found);
            state.sources.put(origin.immutable(), result);
            return result;
        }
    }

    private static final class LevelSources {
        private final Map<BlockPos, List<BlockPos>> sources = new HashMap<>();
        private final Map<BlockPos, Long> delays = new HashMap<>();
    }

    private static @Nullable ServerLevel ownedLevel(@Nullable BlockEntity consumer) {
        return consumer != null && consumer.getLevel() instanceof ServerLevel level
                && level.getServer().isSameThread() ? level : null;
    }

    private static @Nullable ServerLevel validConsumer(@Nullable BlockEntity consumer,
                                                       @Nullable Aspect aspect, int range) {
        ServerLevel level = ownedLevel(consumer);
        if (level == null || aspect == null || range < 1 || range > MAX_RANGE || consumer.isRemoved()
                || loadedBlockEntity(level, consumer.getBlockPos()) != consumer) return null;
        return level;
    }

    private static boolean validEndpoint(@Nullable ServerLevel level, @Nullable BlockEntity tile) {
        return level != null && level.getServer().isSameThread() && tile != null && tile.getLevel() == level
                && !tile.isRemoved() && loadedBlockEntity(level, tile.getBlockPos()) == tile;
    }

    private static boolean adjacent(BlockEntity consumer, BlockEntity endpoint, @Nullable Direction face) {
        return face != null && endpoint.getBlockPos().relative(face).equals(consumer.getBlockPos());
    }

    private static boolean validDestination(@Nullable ServerLevel level, BlockEntity consumer,
                                             BlockEntity destination, @Nullable Direction face) {
        return nativeTransport(destination) && validEndpoint(level, destination)
                && validEndpoint(level, consumer) && adjacent(consumer, destination, face)
                && ((IEssentiaTransport) destination).isConnectable(face)
                && ((IEssentiaTransport) destination).canInputFrom(face);
    }

    /** Exact classes prevent an uncontracted callback subclass from entering a paid transfer. */
    private static boolean nativeTransport(@Nullable BlockEntity endpoint) {
        if (endpoint == null) return false;
        Class<?> type = endpoint.getClass();
        return type == EssentiaJarBlockEntity.class || type == AlembicBlockEntity.class
                || type == TubeBlockEntity.class || type == TubeBufferBlockEntity.class
                || type == TubeFilterBlockEntity.class || type == CentrifugeBlockEntity.class
                || type == ThaumatoriumBlockEntity.class || type == ThaumatoriumTopBlockEntity.class
                || type == GolemPressBlockEntity.class;
    }

    /** Rollback never recreates/replaces a removed endpoint or an unloaded chunk. */
    private static void restore(ServerLevel level, BlockEntity endpoint, CompoundTag snapshot) {
        if (!validEndpoint(level, endpoint)) return;
        endpoint.load(snapshot);
        endpoint.setChanged();
        level.sendBlockUpdated(endpoint.getBlockPos(), endpoint.getBlockState(), endpoint.getBlockState(), 3);
    }

    /** Modern adaptation: query an already present FULL chunk, never load/promote one. */
    static @Nullable BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockEntity tile = chunk.getBlockEntity(pos);
        return tile == null || tile.isRemoved() ? null : tile;
    }

    /** Original asymmetric box and scan order; package access allows exact boundary tests. */
    static void forEachCandidate(BlockPos origin, @Nullable Direction direction, int range, Consumer<BlockPos> visitor) {
        int start = direction == null ? -range : 0;
        Direction side = direction == null ? Direction.UP : direction;
        for (int aa = -range; aa <= range; aa++) {
            for (int bb = -range; bb <= range; bb++) {
                for (int cc = start; cc < range; cc++) {
                    if (aa == 0 && bb == 0 && cc == 0) continue;
                    BlockPos pos;
                    if (side.getStepY() != 0) pos = origin.offset(aa, cc * side.getStepY(), bb);
                    else if (side.getStepX() == 0) pos = origin.offset(aa, bb, cc * side.getStepZ());
                    else pos = origin.offset(cc * side.getStepX(), aa, bb);
                    visitor.accept(pos);
                }
            }
        }
    }
}
