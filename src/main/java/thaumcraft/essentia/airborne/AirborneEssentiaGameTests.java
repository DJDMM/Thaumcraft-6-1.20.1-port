package thaumcraft.essentia.airborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.essentia.transport.TubeBlockEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Actual registered jars and consumers; scoped clocks never change the runtime manager. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class AirborneEssentiaGameTests {
    private static final String TEMPLATE = "essentia_production";
    private static final BlockPos CENTER = new BlockPos(6, 4, 6);

    private AirborneEssentiaGameTests() {}

    private static AlembicBlockEntity consumer(GameTestHelper helper) {
        return consumerAt(helper, helper.absolutePos(CENTER));
    }

    private static AlembicBlockEntity consumerAt(GameTestHelper helper, BlockPos absolute) {
        helper.getLevel().setBlockAndUpdate(absolute, CatalogBlocks.block("alembic").defaultBlockState());
        return (AlembicBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static EssentiaJarBlockEntity jar(GameTestHelper helper, BlockPos absolute,
                                             String id, Aspect aspect, int amount) {
        helper.getLevel().setBlockAndUpdate(absolute, CatalogBlocks.block(id).defaultBlockState());
        var jar = (EssentiaJarBlockEntity) helper.getLevel().getBlockEntity(absolute);
        if (amount > 0) helper.assertTrue(jar.addExact(aspect, amount), "Could not prepare source " + absolute);
        return jar;
    }

    private static EssentiaJarBlockEntity jar(GameTestHelper helper, BlockPos absolute, Aspect aspect, int amount) {
        return jar(helper, absolute, "jar_normal", aspect, amount);
    }

    private static AirborneEssentiaManager.SourceCache cache(AtomicLong clock) {
        return new AirborneEssentiaManager.SourceCache(clock::get);
    }

    private static AirborneEssentiaManager.Transfer drain(GameTestHelper helper,
            AirborneEssentiaManager.SourceCache cache, BlockEntity consumer, Aspect aspect,
            Direction direction, int range) {
        var result = cache.drain(consumer, aspect, direction, range, 0);
        helper.assertTrue(result.isPresent(), "Airborne debit failed for " + aspect + " / " + direction);
        return result.orElseThrow();
    }

    @GameTest(template = TEMPLATE)
    public static void runtimeDrainDebitsOneWithOriginalTrailAndPersistsTheJar(GameTestHelper helper) {
        var consumer = consumer(helper);
        var jar = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 10);
        AirborneEssentiaManager.forgetConsumer(consumer);
        var result = AirborneEssentiaManager.drain(consumer, Aspect.AIR, null, 3, 31);
        helper.assertTrue(result.isPresent() && jar.amount() == 9, "Runtime drain did not debit exactly one");
        var transfer = result.orElseThrow();
        helper.assertTrue(transfer.source().equals(jar.getBlockPos())
                        && transfer.target().equals(consumer.getBlockPos().below())
                        && transfer.aspect() == Aspect.AIR && transfer.ext() == 31,
                "Original source/altar trail endpoints or ext were lost");
        CompoundTag saved = jar.saveWithoutMetadata();
        helper.getLevel().setBlockAndUpdate(jar.getBlockPos(), Blocks.AIR.defaultBlockState());
        var reloaded = jar(helper, jar.getBlockPos(), Aspect.AIR, 0);
        reloaded.load(saved);
        helper.assertTrue(reloaded.amount() == 9 && saved.equals(reloaded.saveWithoutMetadata()),
                "The successful debit did not survive jar NBT reload");
        helper.assertTrue(AirborneEssentiaManager.drain(consumer, Aspect.AIR, null, 3, 0).isPresent()
                        && reloaded.amount() == 8,
                "Cached coordinates retained a stale tile or lost the next debit");
        AirborneEssentiaManager.forgetConsumer(consumer);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void braceBlocksAirborneButLeavesTopTubeAccess(GameTestHelper helper) {
        var consumer = consumer(helper);
        var blocked = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        var usable = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 4);
        helper.assertTrue(blocked.installBrace(), "Brace fixture failed");
        CompoundTag before = blocked.saveWithoutMetadata();
        var transfer = drain(helper, cache(new AtomicLong()), consumer, Aspect.AIR, null, 3);
        helper.assertTrue(transfer.source().equals(usable.getBlockPos()) && usable.amount() == 3
                        && before.equals(blocked.saveWithoutMetadata()),
                "A braced source was drained or prevented use of the next source");
        helper.assertTrue(blocked.canOutputTo(Direction.UP)
                        && blocked.takeEssentia(Aspect.AIR, 1, Direction.UP) == 1 && blocked.amount() == 2,
                "Airborne brace incorrectly closed the original top tube port");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void labelledVoidJarDrainsItsStoredAspectWithoutFilterOrSuctionGate(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), "jar_void", Aspect.AIR, 3);
        CompoundTag tag = source.saveWithoutMetadata();
        tag.putString("AspectFilter", Aspect.FIRE.getTag());
        source.load(tag);
        var other = jar(helper, consumer.getBlockPos().east(), Aspect.FIRE, 5);
        helper.assertTrue(!source.doesContainerAccept(Aspect.AIR) && source.filter() == Aspect.FIRE,
                "Low-level mismatched-label fixture was not installed");
        var transfer = drain(helper, cache(new AtomicLong()), consumer, Aspect.AIR, null, 3);
        helper.assertTrue(transfer.source().equals(source.getBlockPos()) && source.amount() == 2
                        && source.aspect() == Aspect.AIR && source.filter() == Aspect.FIRE && other.amount() == 5,
                "Airborne drain invented a label/suction gate or took the wrong stored aspect");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void alembicsPipesAndCatalogueMirrorsAreNotAirborneSources(GameTestHelper helper) {
        var consumer = consumer(helper);
        helper.assertTrue(consumer.addExact(Aspect.AIR, 8), "Consumer fixture failed");
        var alembic = consumerAt(helper, consumer.getBlockPos().west());
        helper.assertTrue(alembic.addExact(Aspect.AIR, 3), "Alembic fixture failed");
        BlockPos pipePos = consumer.getBlockPos().east();
        helper.getLevel().setBlockAndUpdate(pipePos, CatalogBlocks.block("tube").defaultBlockState());
        var pipe = (TubeBlockEntity) helper.getLevel().getBlockEntity(pipePos);
        helper.assertTrue(pipe.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Pipe fixture failed");
        BlockPos mirrorPos = consumer.getBlockPos().north();
        helper.getLevel().setBlockAndUpdate(mirrorPos, CatalogBlocks.block("mirror_essentia").defaultBlockState());
        helper.assertTrue(!((BlockEntity) consumer instanceof IAspectSource)
                        && !((BlockEntity) alembic instanceof IAspectSource)
                        && !((BlockEntity) pipe instanceof IAspectSource)
                        && !(helper.getLevel().getBlockEntity(mirrorPos) instanceof IAspectSource),
                "An unimplemented device was advertised as an airborne source");
        var cache = cache(new AtomicLong());
        helper.assertTrue(!cache.find(consumer, Aspect.AIR, null, 3)
                        && cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && consumer.amount() == 8 && alembic.amount() == 3 && pipe.getEssentiaAmount(null) == 1,
                "Airborne access stole from a non-source container or its own origin");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void closestVoidJarWinsThroughWallBeforeFartherLabelledNormalJar(GameTestHelper helper) {
        var consumer = consumer(helper);
        var near = jar(helper, consumer.getBlockPos().west(2), "jar_void", Aspect.AIR, 3);
        var far = jar(helper, consumer.getBlockPos().east(3), Aspect.AIR, 4);
        CompoundTag tag = far.saveWithoutMetadata();
        tag.putString("AspectFilter", Aspect.AIR.getTag());
        far.load(tag);
        helper.getLevel().setBlockAndUpdate(consumer.getBlockPos().west(), Blocks.STONE.defaultBlockState());
        var transfer = drain(helper, cache(new AtomicLong()), consumer, Aspect.AIR, null, 3);
        helper.assertTrue(transfer.source().equals(near.getBlockPos()) && near.amount() == 2 && far.amount() == 4,
                "Wall, label, normal-jar or suction priority overrode original squared distance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void equalDistancesKeepOriginalXThenZThenYScanOrder(GameTestHelper helper) {
        var consumer = consumer(helper);
        BlockPos origin = consumer.getBlockPos();
        List<BlockPos> order = List.of(origin.west(), origin.north(), origin.below(),
                origin.above(), origin.south(), origin.east());
        var jars = order.stream().map(pos -> jar(helper, pos, Aspect.AIR, 1)).toList();
        var cache = cache(new AtomicLong());
        for (BlockPos expected : order)
            helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, null, 3).source().equals(expected),
                    "An equal-distance source changed the strict insertion tie order: " + expected);
        helper.assertTrue(jars.stream().allMatch(jar -> jar.amount() == 0), "Tie sequence lost or duplicated a unit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void realJarBoxIncludesCornersAndLowerBoundaryButExcludesUpperLayer(GameTestHelper helper) {
        var consumer = consumer(helper);
        BlockPos origin = consumer.getBlockPos();
        Set<BlockPos> included = Set.of(origin.west(3), origin.east(3), origin.north(3), origin.south(3),
                origin.below(3), origin.above(2), origin.offset(-3, -3, -3));
        var valid = included.stream().map(pos -> jar(helper, pos, Aspect.AIR, 1)).toList();
        var excluded = List.of(jar(helper, origin.above(3), Aspect.AIR, 1),
                jar(helper, origin.west(4), Aspect.AIR, 1), jar(helper, origin.north(4), Aspect.AIR, 1));
        var cache = cache(new AtomicLong());
        Set<BlockPos> debited = new HashSet<>();
        for (int n = 0; n < included.size(); n++) debited.add(drain(helper, cache, consumer, Aspect.AIR, null, 3).source());
        helper.assertTrue(debited.equals(included) && valid.stream().allMatch(jar -> jar.amount() == 0)
                        && excluded.stream().allMatch(jar -> jar.amount() == 1)
                        && cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty(),
                "Airborne box became a sphere, a symmetric cube or an altar-relative range");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void matrixVolumeAndAllSixDirectionalBoundariesMatchRelease(GameTestHelper helper) {
        Set<BlockPos> matrix = new HashSet<>();
        AirborneEssentiaManager.forEachCandidate(BlockPos.ZERO, null, 12, matrix::add);
        helper.assertTrue(matrix.size() == 14_999 && !matrix.contains(BlockPos.ZERO)
                        && matrix.contains(new BlockPos(-12, -12, -12))
                        && matrix.contains(new BlockPos(12, 11, 12))
                        && !matrix.contains(new BlockPos(0, 12, 0))
                        && !matrix.contains(new BlockPos(13, 0, 0)),
                "Matrix range12 no longer has its original 25x25x24-minus-origin volume");
        for (Direction side : Direction.values()) {
            Set<BlockPos> positions = new HashSet<>();
            AirborneEssentiaManager.forEachCandidate(BlockPos.ZERO, side, 3, positions::add);
            Direction transverse = side.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
            helper.assertTrue(positions.size() == 146 && !positions.contains(BlockPos.ZERO)
                            && positions.contains(BlockPos.ZERO.relative(side, 2))
                            && !positions.contains(BlockPos.ZERO.relative(side, 3))
                            && !positions.contains(BlockPos.ZERO.relative(side.getOpposite()))
                            && positions.contains(BlockPos.ZERO.relative(transverse, 3))
                            && positions.contains(BlockPos.ZERO.relative(transverse.getOpposite(), 3)),
                    "Directional volume lost its same-plane or exclusive front boundary for " + side);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void directionalDrainsUseForwardBoxAndTheConsumerPlaneForAllSixSides(GameTestHelper helper) {
        var consumer = consumer(helper);
        BlockPos origin = consumer.getBlockPos();
        for (Direction side : Direction.values()) {
            Direction transverse = side.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
            var plane = jar(helper, origin.relative(transverse), Aspect.AIR, 1);
            var forward = jar(helper, origin.relative(side, 2), Aspect.AIR, 1);
            var excludedFront = jar(helper, origin.relative(side, 3), Aspect.AIR, 1);
            var excludedBack = jar(helper, origin.relative(side.getOpposite()), Aspect.AIR, 1);
            var cache = cache(new AtomicLong());
            helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, side, 3).source().equals(plane.getBlockPos())
                            && drain(helper, cache, consumer, Aspect.AIR, side, 3).source().equals(forward.getBlockPos())
                            && cache.drain(consumer, Aspect.AIR, side, 3, 0).isEmpty()
                            && excludedFront.amount() == 1 && excludedBack.amount() == 1,
                    "Actual directional debit crossed a release boundary for " + side);
            for (var jar : List.of(plane, forward, excludedFront, excludedBack))
                helper.getLevel().setBlockAndUpdate(jar.getBlockPos(), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void successfulSourceCacheHasNoTtlAndRefreshDiscoversNewCloserJar(GameTestHelper helper) {
        var consumer = consumer(helper);
        var old = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 5);
        var clock = new AtomicLong();
        var cache = cache(clock);
        drain(helper, cache, consumer, Aspect.AIR, null, 3);
        var newer = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 5);
        clock.set(1_000_000L);
        helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, null, 3).source().equals(old.getBlockPos())
                        && old.amount() == 3 && newer.amount() == 5,
                "A successful cache acquired a TTL or discovered newly placed sources automatically");
        cache.refreshSources(consumer);
        helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, null, 3).source().equals(newer.getBlockPos())
                        && newer.amount() == 4,
                "Explicit refresh did not rescan a successful source cache");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sourceCacheKeyIgnoresLaterAspectDirectionAndRange(GameTestHelper helper) {
        var consumer = consumer(helper);
        var west = jar(helper, consumer.getBlockPos().west(2), Aspect.AIR, 1);
        var east = jar(helper, consumer.getBlockPos().east(3), Aspect.FIRE, 2);
        var cache = cache(new AtomicLong());
        helper.assertTrue(drain(helper, cache, consumer, Aspect.FIRE, null, 3).source().equals(east.getBlockPos())
                        && drain(helper, cache, consumer, Aspect.AIR, Direction.EAST, 1).source().equals(west.getBlockPos())
                        && drain(helper, cache, consumer, Aspect.FIRE, Direction.WEST, 1).source().equals(east.getBlockPos())
                        && west.amount() == 0 && east.amount() == 0,
                "Later parameters changed the BETA26 consumer-position-only source cache");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void failedDrainDelaysAllAspectsAndRefreshKeepsExactTenSecondDeadline(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), Aspect.FIRE, 3);
        var clock = new AtomicLong(100L);
        var cache = cache(clock);
        helper.assertTrue(cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty(), "Wrong aspect drain succeeded");
        cache.refreshSources(consumer);
        helper.assertTrue(cache.drain(consumer, Aspect.FIRE, null, 3, 0).isEmpty() && source.amount() == 3,
                "An aspect change or refresh bypassed the shared real-time retry delay");
        clock.set(10_099L);
        helper.assertTrue(cache.drain(consumer, Aspect.FIRE, null, 3, 0).isEmpty() && source.amount() == 3,
                "Retry was allowed before the exact ten-second wall-clock deadline");
        clock.set(10_100L);
        helper.assertTrue(drain(helper, cache, consumer, Aspect.FIRE, null, 3).source().equals(source.getBlockPos())
                        && source.amount() == 2,
                "A retry remained delayed at equality with the release deadline");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptyDiscoveryKeepsNewSourceInvisibleUntilExactExpiry(GameTestHelper helper) {
        var consumer = consumer(helper);
        var clock = new AtomicLong();
        var cache = cache(clock);
        helper.assertTrue(!cache.find(consumer, Aspect.AIR, null, 3), "Empty fixture unexpectedly contains a source");
        var added = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 1);
        clock.set(9_999L);
        helper.assertTrue(!cache.find(consumer, Aspect.AIR, null, 3) && added.amount() == 1,
                "Empty discovery rescanned or mutated a new source before expiry");
        clock.set(10_000L);
        helper.assertTrue(cache.find(consumer, Aspect.AIR, null, 3) && added.amount() == 1,
                "Read-only retry at the deadline failed or paid the aspect");
        drain(helper, cache, consumer, Aspect.AIR, null, 3);
        helper.assertTrue(added.amount() == 0, "Post-expiry drain did not debit the discovered unit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void missingEarlierCachedSourceBreaksBeforeHealthyLaterSource(GameTestHelper helper) {
        var consumer = consumer(helper);
        var earlier = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 2);
        var later = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        var clock = new AtomicLong();
        var cache = cache(clock);
        helper.assertTrue(cache.find(consumer, Aspect.AIR, null, 3), "Could not build positive source cache");
        helper.getLevel().setBlockAndUpdate(earlier.getBlockPos(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!cache.find(consumer, Aspect.AIR, null, 3)
                        && !cache.find(consumer, Aspect.AIR, null, 3)
                        && cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty() && later.amount() == 3,
                "A stale earlier coordinate was skipped instead of breaking the cached traversal");
        clock.set(9_999L);
        helper.assertTrue(cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty() && later.amount() == 3,
                "Stale-source failure failed to impose the retry delay");
        clock.set(10_000L);
        helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, null, 3).source().equals(later.getBlockPos())
                        && later.amount() == 2,
                "Expiry did not rebuild the cache after its missing earlier source");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void failedReadOnlyFindKeepsPositiveCacheAndDoesNotDelayOrMutateJars(GameTestHelper helper) {
        var consumer = consumer(helper);
        var air = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        var fire = jar(helper, consumer.getBlockPos().east(2), Aspect.FIRE, 3);
        var cache = cache(new AtomicLong());
        CompoundTag airBefore = air.saveWithoutMetadata(), fireBefore = fire.saveWithoutMetadata();
        helper.assertTrue(cache.find(consumer, Aspect.AIR, null, 3)
                        && !cache.find(consumer, Aspect.EARTH, null, 3)
                        && airBefore.equals(air.saveWithoutMetadata()) && fireBefore.equals(fire.saveWithoutMetadata()),
                "A read-only query changed source amount, label or saved state");
        var newer = jar(helper, consumer.getBlockPos().below(), Aspect.EARTH, 3);
        helper.assertTrue(!cache.find(consumer, Aspect.EARTH, null, 3), "Failed find discarded its positive cache");
        helper.assertTrue(drain(helper, cache, consumer, Aspect.FIRE, null, 3).source().equals(fire.getBlockPos())
                        && fire.amount() == 2 && newer.amount() == 3,
                "Failed find installed a delay or extracted from the new uncached source");
        cache.refreshSources(consumer);
        CompoundTag newBefore = newer.saveWithoutMetadata();
        helper.assertTrue(cache.find(consumer, Aspect.EARTH, null, 3)
                        && newBefore.equals(newer.saveWithoutMetadata()), "Refreshed read-only find performed a debit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void twoConsumersCannotDuplicateOneUnitAndRemovalClearsTheirDelay(GameTestHelper helper) {
        var first = consumer(helper);
        var second = consumerAt(helper, first.getBlockPos().south(2));
        var source = jar(helper, first.getBlockPos().west(), Aspect.AIR, 1);
        var cache = cache(new AtomicLong());
        int successes = cache.drain(first, Aspect.AIR, null, 3, 0).isPresent() ? 1 : 0;
        successes += cache.drain(second, Aspect.AIR, null, 3, 0).isPresent() ? 1 : 0;
        helper.assertTrue(successes == 1 && source.amount() == 0, "Competing consumers duplicated or lost one stored unit");
        helper.assertTrue(source.addExact(Aspect.AIR, 1), "Could not refill shared source");
        helper.assertTrue(cache.drain(second, Aspect.AIR, null, 3, 0).isEmpty(), "Second consumer's delay unexpectedly vanished");
        cache.forgetConsumer(second);
        helper.assertTrue(drain(helper, cache, second, Aspect.AIR, null, 3).source().equals(source.getBlockPos())
                        && source.amount() == 0, "Consumer removal failed to forget its list and delay");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void levelAndServerCleanupForgetPositiveAndNegativeCaches(GameTestHelper helper) {
        var consumer = consumer(helper);
        var old = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.find(consumer, Aspect.AIR, null, 3), "Could not build lifecycle fixture");
        var nearer = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        cache.forgetLevel(helper.getLevel());
        helper.assertTrue(drain(helper, cache, consumer, Aspect.AIR, null, 3).source().equals(nearer.getBlockPos())
                        && old.amount() == 3, "Level cleanup retained a previous world's discovered sources");
        helper.assertTrue(cache.drain(consumer, Aspect.WATER, null, 3, 0).isEmpty(), "Could not build negative lifecycle cache");
        var water = jar(helper, consumer.getBlockPos().below(2), Aspect.WATER, 2);
        cache.forgetServer(helper.getLevel().getServer());
        helper.assertTrue(drain(helper, cache, consumer, Aspect.WATER, null, 3).source().equals(water.getBlockPos())
                        && water.amount() == 1, "Server cleanup retained a previous session's retry delay");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unloadedAndOutsideHeightQueriesNeverCreateOrPromoteChunks(GameTestHelper helper) {
        var consumer = consumer(helper);
        var level = helper.getLevel();
        BlockPos remote = consumer.getBlockPos().offset(1_000_000, 0, 1_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "The remote no-load fixture was unexpectedly already present");
        helper.assertTrue(AirborneEssentiaManager.loadedBlockEntity(level, remote) == null,
                "An unloaded coordinate returned a tile");
        var detached = new AlembicBlockEntity(remote, consumer.getBlockState());
        detached.setLevel(level);
        helper.assertTrue(cache(new AtomicLong()).drain(detached, Aspect.AIR, null, 16, 0).isEmpty()
                        && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "Airborne lookup loaded/promoted a remote chunk for a detached consumer");
        BlockPos tooHigh = new BlockPos(remote.getX(), level.getMaxBuildHeight(), remote.getZ());
        BlockPos tooLow = new BlockPos(remote.getX(), level.getMinBuildHeight() - 1, remote.getZ());
        helper.assertTrue(AirborneEssentiaManager.loadedBlockEntity(level, tooHigh) == null
                        && AirborneEssentiaManager.loadedBlockEntity(level, tooLow) == null
                        && level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "Outside-height lookup accessed or created a chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void invalidDetachedRemovedAndOffThreadCallsCannotDebitOrPoisonCache(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        var cache = cache(new AtomicLong());
        CompoundTag before = source.saveWithoutMetadata();
        var detached = new AlembicBlockEntity(consumer.getBlockPos(), consumer.getBlockState());
        helper.assertTrue(cache.drain(detached, Aspect.AIR, null, 3, 0).isEmpty(), "A worldless consumer drained a source");
        detached.setLevel(helper.getLevel());
        helper.assertTrue(cache.drain(detached, Aspect.AIR, null, 3, 0).isEmpty(), "A tile outside the world's BE map drained a source");
        helper.assertTrue(cache.drain(consumer, null, null, 3, 0).isEmpty()
                        && cache.drain(null, Aspect.AIR, null, 3, 0).isEmpty()
                        && cache.drain(consumer, Aspect.AIR, null, 0, 0).isEmpty()
                        && cache.drain(consumer, Aspect.AIR, null, -1, 0).isEmpty()
                        && cache.drain(consumer, Aspect.AIR, null, 17, 0).isEmpty()
                        && !cache.find(consumer, null, null, 3), "An invalid aspect/range was accepted");
        consumer.setRemoved();
        helper.assertTrue(cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty(), "A removed consumer drained a source");
        consumer.clearRemoved();
        boolean offThread = CompletableFuture.supplyAsync(() -> cache.drain(consumer, Aspect.AIR, null, 3, 0).isPresent()).join();
        helper.assertTrue(!offThread && before.equals(source.saveWithoutMetadata()),
                "An off-thread or rejected request changed a jar");
        drain(helper, cache, consumer, Aspect.AIR, null, 3);
        helper.assertTrue(source.amount() == 2, "Invalid requests poisoned the valid consumer's source cache");
        helper.succeed();
    }
}
