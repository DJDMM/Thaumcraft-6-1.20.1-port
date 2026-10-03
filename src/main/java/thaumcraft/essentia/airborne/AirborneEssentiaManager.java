package thaumcraft.essentia.airborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Only the drain/find/refresh operations used by the infusion matrix are implemented;
 * this does not implement essentia mirrors, transfuser insertion or confirmed drain.
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
        private final Map<ServerLevel, LevelSources> levels = new IdentityHashMap<>();

        SourceCache(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

        synchronized Optional<Transfer> drain(BlockEntity consumer, Aspect aspect,
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

        synchronized void refreshSources(BlockEntity consumer) {
            ServerLevel level = ownedLevel(consumer);
            if (level != null && levels.containsKey(level)) levels.get(level).sources.remove(consumer.getBlockPos());
        }

        synchronized void forgetConsumer(BlockEntity consumer) {
            ServerLevel level = ownedLevel(consumer);
            if (level == null) return;
            LevelSources state = levels.get(level);
            if (state == null) return;
            state.sources.remove(consumer.getBlockPos());
            state.delays.remove(consumer.getBlockPos());
            if (state.sources.isEmpty() && state.delays.isEmpty()) levels.remove(level);
        }

        synchronized void forgetLevel(ServerLevel level) { levels.remove(level); }

        synchronized void forgetServer(MinecraftServer server) {
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
