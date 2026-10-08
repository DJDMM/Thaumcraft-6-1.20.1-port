package thaumcraft.essentia.airborne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.essentia.transport.TubeBufferBlockEntity;
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

    @GameTest(template = TEMPLATE)
    public static void runtimeInsertionFillsFartherNonemptyBeforeCloserEmptyWithOriginalFx(GameTestHelper helper) {
        var consumer = consumer(helper);
        var near = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 0);
        var far = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        AirborneEssentiaManager.forgetConsumer(consumer);
        var transfer = AirborneEssentiaManager.add(consumer, Aspect.AIR, null, 3, 5).orElseThrow();
        helper.assertTrue(near.amount() == 0 && far.amount() == 4
                        && transfer.source().equals(consumer.getBlockPos())
                        && transfer.target().equals(far.getBlockPos()) && transfer.ext() == 5,
                "Insertion lost nonempty priority, one unit or transfuser-to-jar FX endpoints");
        AirborneEssentiaManager.forgetConsumer(consumer);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void insertionSkipsFullNormalAndWrongTypeThenKeepsNearestEmptyTieOrder(GameTestHelper helper) {
        var consumer = consumer(helper);
        var full = jar(helper, consumer.getBlockPos().above(), Aspect.AIR, EssentiaJarBlockEntity.CAPACITY);
        var wrongType = jar(helper, consumer.getBlockPos().east(), Aspect.FIRE, 3);
        var west = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 0);
        var north = jar(helper, consumer.getBlockPos().north(), Aspect.AIR, 0);
        var cache = cache(new AtomicLong());
        var transfer = cache.add(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        helper.assertTrue(transfer.target().equals(west.getBlockPos()) && west.amount() == 1 && north.amount() == 0
                        && full.amount() == EssentiaJarBlockEntity.CAPACITY && wrongType.amount() == 3,
                "Insertion bypassed capacity/stored type or reordered equal-distance empty destinations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void insertionHonorsBraceAndLabelWhileVoidOverflowIsSuccessful(GameTestHelper helper) {
        var consumer = consumer(helper);
        var blocked = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 2);
        helper.assertTrue(blocked.installBrace(), "Could not brace destination");
        var labelled = jar(helper, consumer.getBlockPos().north(), Aspect.AIR, 0);
        CompoundTag label = labelled.saveWithoutMetadata(); label.putString("AspectFilter", Aspect.FIRE.getTag());
        labelled.load(label);
        var overflow = jar(helper, consumer.getBlockPos().east(2), "jar_void", Aspect.AIR, EssentiaJarBlockEntity.CAPACITY);
        var cache = cache(new AtomicLong());
        var transfer = cache.add(consumer, Aspect.AIR, null, 3, 5).orElseThrow();
        helper.assertTrue(transfer.target().equals(overflow.getBlockPos())
                        && overflow.amount() == EssentiaJarBlockEntity.CAPACITY && blocked.amount() == 2
                        && labelled.amount() == 0 && labelled.filter() == Aspect.FIRE,
                "Insertion bypassed a brace/label or refused the original void overflow sink");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void failedInsertionSharesExactRetryDelayWithDrainFindAndRefresh(GameTestHelper helper) {
        var consumer = consumer(helper);
        var full = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, EssentiaJarBlockEntity.CAPACITY);
        var clock = new AtomicLong(11); var cache = cache(clock);
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).isEmpty(), "A full normal jar accepted overflow");
        var newer = jar(helper, consumer.getBlockPos().east(), Aspect.AIR, 0);
        cache.refreshSources(consumer);
        clock.set(10_010);
        helper.assertTrue(!cache.find(consumer, Aspect.AIR, null, 3)
                        && cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && cache.add(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && full.amount() == EssentiaJarBlockEntity.CAPACITY && newer.amount() == 0,
                "Insertion delay was not shared or refresh removed its precise wall-clock deadline");
        clock.set(10_011);
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).orElseThrow().target().equals(newer.getBlockPos())
                        && newer.amount() == 1, "Insertion stayed delayed at exact deadline equality");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void insertionReusesDrainCacheRegardlessOfLaterDirectionRangeAndWallClock(GameTestHelper helper) {
        var consumer = consumer(helper);
        var old = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        var clock = new AtomicLong(); var cache = cache(clock);
        helper.assertTrue(cache.find(consumer, Aspect.AIR, Direction.EAST, 3), "Could not build directional cache");
        var newer = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 1);
        clock.set(1_000_000);
        helper.assertTrue(cache.add(consumer, Aspect.AIR, Direction.WEST, 1, 0).orElseThrow().target().equals(old.getBlockPos())
                        && old.amount() == 4 && newer.amount() == 1,
                "Insertion invented operation/range/direction cache keys or a positive-cache TTL");
        cache.refreshSources(consumer);
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).orElseThrow().target().equals(newer.getBlockPos())
                        && newer.amount() == 2, "Explicit refresh did not rebuild insertion priority");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void insertionStaleBreakStillAllowsPreviouslyDeferredEmptyThenDelaysIfNone(GameTestHelper helper) {
        var consumer = consumer(helper);
        var empty = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 0);
        var stale = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 1);
        var later = jar(helper, consumer.getBlockPos().south(3), Aspect.AIR, 2);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.find(consumer, Aspect.AIR, null, 3), "Could not build deferred-empty fixture");
        helper.getLevel().setBlockAndUpdate(stale.getBlockPos(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).orElseThrow().target().equals(empty.getBlockPos())
                        && empty.amount() == 1 && later.amount() == 2,
                "Insertion changed the original stale-break plus earlier-empty second pass");
        helper.getLevel().setBlockAndUpdate(empty.getBlockPos(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && cache.drain(consumer, Aspect.AIR, null, 3, 0).isEmpty() && later.amount() == 2,
                "Insertion skipped a missing earlier source or omitted its shared failure delay");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void drainConfirmationsAreReadOnlyConsumerOwnedAndCannotDuplicateOneUnit(GameTestHelper helper) {
        var first = consumer(helper);
        var second = consumerAt(helper, first.getBlockPos().south(2));
        var source = jar(helper, first.getBlockPos().west(), Aspect.AIR, 1);
        var cache = cache(new AtomicLong());
        CompoundTag before = source.saveWithoutMetadata();
        var firstPreview = cache.prepareDrain(first, Aspect.AIR, null, 3, 5).orElseThrow();
        var secondPreview = cache.prepareDrain(second, Aspect.AIR, null, 3, 7).orElseThrow();
        helper.assertTrue(before.equals(source.saveWithoutMetadata())
                        && cache.confirmDrain(second, firstPreview).isEmpty(),
                "Confirmation preview debited or another consumer stole the promise");
        var transfer = cache.confirmDrain(first, firstPreview).orElseThrow();
        helper.assertTrue(source.amount() == 0 && transfer.target().equals(first.getBlockPos()) && transfer.ext() == 5
                        && cache.confirmDrain(first, firstPreview).isEmpty()
                        && cache.confirmDrain(second, secondPreview).isEmpty(),
                "Confirmations overwrote ownership, replayed or duplicated one actual stored unit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void confirmationRevalidatesBraceReplacedSourceAndRemovedConsumer(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        var cache = cache(new AtomicLong());
        var braced = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        helper.assertTrue(source.installBrace() && cache.confirmDrain(consumer, braced).isEmpty() && source.amount() == 3,
                "A source braced after preview still debited");
        CompoundTag unbrace = source.saveWithoutMetadata(); unbrace.putBoolean("blocked", false); source.load(unbrace);
        var replaced = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        BlockPos pos = source.getBlockPos(); helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var replacement = jar(helper, pos, Aspect.AIR, 7);
        helper.assertTrue(cache.confirmDrain(consumer, replaced).isEmpty() && replacement.amount() == 7,
                "Confirmation debited a replacement tile at the old coordinate");
        var removed = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        consumer.setRemoved();
        helper.assertTrue(cache.confirmDrain(consumer, removed).isEmpty() && replacement.amount() == 7,
                "Removed consumer committed an old preview");
        consumer.clearRemoved();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void failedConfirmationDiscoveryDelaysOtherOperationsWithoutExtracting(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), Aspect.FIRE, 3);
        var clock = new AtomicLong(); var cache = cache(clock);
        helper.assertTrue(cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && cache.prepareDrain(consumer, Aspect.FIRE, null, 3, 0).isEmpty()
                        && cache.add(consumer, Aspect.FIRE, null, 3, 0).isEmpty()
                        && source.amount() == 3, "Failed confirmation did not share the original ten-second retry delay");
        clock.set(10_000);
        var preview = cache.prepareDrain(consumer, Aspect.FIRE, null, 3, 0).orElseThrow();
        helper.assertTrue(source.amount() == 3 && cache.confirmDrain(consumer, preview).isPresent() && source.amount() == 2,
                "Confirmation discovery paid early or failed the exact-expiry commit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nativeTubeSourcePaysOneUnitOnlyAfterCompatibleAirborneDestinationExists(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = consumerAt(helper, consumer.getBlockPos().west());
        helper.assertTrue(source.addExact(Aspect.AIR, 2), "Could not prepare physical input");
        var destination = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 0);
        var cache = cache(new AtomicLong());
        for (int i = 0; i < 2; i++) {
            var transfer = cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).orElseThrow();
            helper.assertTrue(transfer.source().equals(consumer.getBlockPos()) && transfer.target().equals(destination.getBlockPos())
                            && source.amount() == 1 - i && destination.amount() == i + 1,
                    "Input transfer lost or duplicated a unit or used tube coordinates for airborne FX");
        }
        helper.assertTrue(cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isEmpty()
                        && source.amount() == 0 && destination.amount() == 2,
                "Empty physical source created an airborne unit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bufferSourceVetoCannotDuplicateAndDoesNotInventDestinationRetryDelay(GameTestHelper helper) {
        var consumer = consumer(helper);
        BlockPos sourcePos = consumer.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(sourcePos, CatalogBlocks.block("tube_buffer").defaultBlockState());
        var source = (TubeBufferBlockEntity) helper.getLevel().getBlockEntity(sourcePos);
        helper.assertTrue(source.addToContainer(Aspect.AIR, 1) == 0, "Could not fill physical buffer");
        var stronger = jar(helper, sourcePos.below(), Aspect.AIR, 3);
        var destination = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 0);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, null, 3, 5).isEmpty()
                        && source.containerContains(Aspect.AIR) == 1 && destination.amount() == 0 && stronger.amount() == 3,
                "Physical buffer's stronger-consumer veto created a destination unit before payment");
        helper.getLevel().setBlockAndUpdate(stronger.getBlockPos(), Blocks.AIR.defaultBlockState());
        cache.refreshSources(consumer);
        helper.assertTrue(cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, null, 3, 5).isPresent()
                        && source.containerContains(Aspect.AIR) == 0 && destination.amount() == 1,
                "A source veto invented an insertion delay or prevented the next exact payment");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nativeOutputRefusalRestoresFullSourceNbtAndCanRetryImmediately(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        CompoundTag labelled = source.saveWithoutMetadata(); labelled.putString("AspectFilter", Aspect.FIRE.getTag()); source.load(labelled);
        var destination = jar(helper, consumer.getBlockPos().below(), Aspect.AIR, EssentiaJarBlockEntity.CAPACITY);
        var cache = cache(new AtomicLong());
        CompoundTag before = source.saveWithoutMetadata(), targetBefore = destination.saveWithoutMetadata();
        helper.assertTrue(cache.transferFromSources(consumer, destination, Aspect.AIR, Direction.UP, Direction.UP, 3, 5).isEmpty()
                        && before.equals(source.saveWithoutMetadata()) && targetBefore.equals(destination.saveWithoutMetadata()),
                "Refused physical output lost a unit or changed the source's mismatched label/NBT");
        helper.assertTrue(destination.takeFromContainer(Aspect.AIR, 1), "Could not free one output slot");
        var transfer = cache.transferFromSources(consumer, destination, Aspect.AIR, Direction.UP, Direction.UP, 3, 5).orElseThrow();
        helper.assertTrue(source.amount() == 2 && source.filter() == Aspect.FIRE && destination.amount() == EssentiaJarBlockEntity.CAPACITY
                        && transfer.source().equals(source.getBlockPos()) && transfer.target().equals(consumer.getBlockPos()),
                "Refused destination invented a discovery delay or the paid output lost endpoint/label semantics");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void voidOverflowConsumesExactlyOnePhysicalInputAndNoStoredDuplicate(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = consumerAt(helper, consumer.getBlockPos().west());
        helper.assertTrue(source.addExact(Aspect.AIR, 2), "Could not prepare void input");
        var destination = jar(helper, consumer.getBlockPos().east(2), "jar_void", Aspect.AIR, EssentiaJarBlockEntity.CAPACITY);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isPresent()
                        && source.amount() == 1 && destination.amount() == EssentiaJarBlockEntity.CAPACITY,
                "Original void overflow did not consume one physical unit or fabricated stored essentia");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void callbackTransportSubclassesFailClosedBeforeEitherEndpointMutation(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        BlockPos destinationPos = consumer.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(destinationPos, CatalogBlocks.block("tube").defaultBlockState());
        var destination = new CallbackTube(destinationPos, helper.getLevel().getBlockState(destinationPos));
        helper.getLevel().setBlockEntity(destination);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.transferFromSources(consumer, destination, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isEmpty()
                        && source.amount() == 3 && destination.calls == 0,
                "An uncontracted transport subclass reached a paid source debit or arbitrary insertion callback");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void directSourceCallbacksCannotReenterDrainInsertionOrConfirmation(GameTestHelper helper) {
        var consumer = consumer(helper);
        BlockPos pos = consumer.getBlockPos().west();
        // A server chest has no ticker, and this subclass remains compatible with its
        // registered CHEST type. A fake JAR type would enter the native jar ticker.
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var source = new CallbackSource(pos, helper.getLevel().getBlockState(pos));
        helper.getLevel().setBlockEntity(source);
        var cache = cache(new AtomicLong()); source.cache = cache; source.consumer = consumer;
        var transfer = cache.drain(consumer, Aspect.AIR, null, 3, 5).orElseThrow();
        helper.assertTrue(transfer.source().equals(pos) && source.units == 1 && source.reentrySuccesses == 0,
                "Source callback recursively drained or inserted another unit");
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 5).isPresent()
                        && source.units == 2 && source.reentrySuccesses == 0,
                "Insertion callback recursively entered the shared transfer manager");
        var preview = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 5).orElseThrow();
        helper.assertTrue(cache.confirmDrain(consumer, preview).isPresent() && source.units == 1 && source.reentrySuccesses == 0,
                "Confirmed extraction permitted a recursive transfer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void confirmationLifecycleAndOffThreadGuardsNeverCommitOrPoisonValidRequests(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 3);
        var cache = cache(new AtomicLong());
        var preview = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 5).orElseThrow();
        boolean offThread = CompletableFuture.supplyAsync(() -> cache.confirmDrain(consumer, preview).isPresent()).join();
        helper.assertTrue(!offThread && source.amount() == 3
                        && cache.confirmDrain(consumer, preview).isPresent() && source.amount() == 2,
                "Off-thread confirmation debited, consumed the valid promise or poisoned its cache");
        var removed = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        cache.forgetConsumer(consumer);
        helper.assertTrue(cache.confirmDrain(consumer, removed).isEmpty() && source.amount() == 2,
                "Consumer cleanup retained a pending confirmation");
        var unloaded = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        cache.forgetLevel(helper.getLevel());
        helper.assertTrue(cache.confirmDrain(consumer, unloaded).isEmpty() && source.amount() == 2,
                "World cleanup retained a pending confirmation");
        var stopped = cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).orElseThrow();
        cache.forgetServer(helper.getLevel().getServer());
        helper.assertTrue(cache.confirmDrain(consumer, stopped).isEmpty() && source.amount() == 2,
                "Server cleanup retained a previous session's pending confirmation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void atomicInputRejectsDetachedRemovedAndOffThreadTransportWithoutPoisoningCache(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = consumerAt(helper, consumer.getBlockPos().west());
        helper.assertTrue(source.addExact(Aspect.AIR, 3), "Could not prepare guarded physical source");
        var target = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 0);
        var detached = new AlembicBlockEntity(source.getBlockPos(), source.getBlockState());
        detached.setLevel(helper.getLevel()); detached.load(source.saveWithoutMetadata());
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.transferToSources(consumer, detached, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isEmpty()
                        && source.amount() == 3 && target.amount() == 0,
                "A detached replacement coordinate entered the paid input transaction");
        source.setRemoved();
        helper.assertTrue(cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isEmpty(),
                "Removed physical source entered the paid transaction");
        source.clearRemoved();
        boolean offThread = CompletableFuture.supplyAsync(() -> cache.transferToSources(consumer, source,
                Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isPresent()).join();
        helper.assertTrue(!offThread && source.amount() == 3 && target.amount() == 0
                        && cache.transferToSources(consumer, source, Aspect.AIR, Direction.EAST, Direction.EAST, 3, 5).isPresent()
                        && source.amount() == 2 && target.amount() == 1,
                "Off-thread input paid or a rejected endpoint poisoned the immediate valid transaction");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void confirmedOutputRejectsReplacedTransportAndConsumesItsPromiseOnlyOnce(GameTestHelper helper) {
        var consumer = consumer(helper);
        var source = jar(helper, consumer.getBlockPos().east(2), Aspect.AIR, 3);
        BlockPos pos = consumer.getBlockPos().below();
        helper.getLevel().setBlockAndUpdate(pos, CatalogBlocks.block("tube").defaultBlockState());
        var oldDestination = helper.getLevel().getBlockEntity(pos);
        var cache = cache(new AtomicLong());
        var preview = cache.prepareDrain(consumer, Aspect.AIR, Direction.UP, 3, 5).orElseThrow();
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var replacement = jar(helper, pos, Aspect.AIR, 0);
        helper.assertTrue(cache.confirmDrainTo(consumer, preview, oldDestination, Direction.UP).isEmpty()
                        && cache.confirmDrainTo(consumer, preview, replacement, Direction.UP).isEmpty()
                        && source.amount() == 3 && replacement.amount() == 0,
                "A replaced transport received the promise or the consumed confirmation replayed onto its replacement");
        var fresh = cache.prepareDrain(consumer, Aspect.AIR, Direction.UP, 3, 5).orElseThrow();
        helper.assertTrue(cache.confirmDrainTo(consumer, fresh, replacement, Direction.UP).isPresent()
                        && source.amount() == 2 && replacement.amount() == 1,
                "Rejecting a replaced output prevented a new valid physical transfer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void insertionRechecksDeferredBraceAfterAnotherSourceCallback(GameTestHelper helper) {
        var consumer = consumer(helper);
        var deferred = jar(helper, consumer.getBlockPos().west(), Aspect.AIR, 0);
        BlockPos callbackPos = consumer.getBlockPos().east(2);
        helper.getLevel().setBlockAndUpdate(callbackPos, Blocks.CHEST.defaultBlockState());
        var callback = new CallbackSource(callbackPos, helper.getLevel().getBlockState(callbackPos));
        callback.accepts = false; callback.acceptanceCallback = deferred::installBrace;
        helper.getLevel().setBlockEntity(callback);
        var cache = cache(new AtomicLong());
        helper.assertTrue(cache.add(consumer, Aspect.AIR, null, 3, 0).isEmpty()
                        && deferred.isBlocked() && deferred.amount() == 0 && callback.units == 2,
                "A deferred empty destination became braced but pass two still awarded a unit");
        helper.succeed();
    }

    private static final class CallbackTube extends TubeBlockEntity {
        private int calls;
        private CallbackTube(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { super(pos, state); }
        @Override public int addEssentia(Aspect aspect, int amount, Direction face) { ++calls; return amount; }
    }

    private static final class CallbackSource extends ChestBlockEntity implements IAspectSource {
        private int units = 2, reentrySuccesses;
        private boolean accepts = true;
        private Runnable acceptanceCallback;
        private AirborneEssentiaManager.SourceCache cache;
        private BlockEntity consumer;
        private CallbackSource(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
        }
        private void reenter() {
            if (cache.drain(consumer, Aspect.AIR, null, 3, 0).isPresent()) ++reentrySuccesses;
            if (cache.add(consumer, Aspect.AIR, null, 3, 0).isPresent()) ++reentrySuccesses;
            if (cache.prepareDrain(consumer, Aspect.AIR, null, 3, 0).isPresent()) ++reentrySuccesses;
        }
        @Override public boolean isBlocked() { return false; }
        @Override public AspectList getAspects() { return new AspectList().add(Aspect.AIR, units); }
        @Override public void setAspects(AspectList list) { units = list.getAmount(Aspect.AIR); }
        @Override public boolean doesContainerAccept(Aspect aspect) {
            if (acceptanceCallback != null) { Runnable callback = acceptanceCallback; acceptanceCallback = null; callback.run(); }
            return accepts && aspect == Aspect.AIR;
        }
        @Override public int addToContainer(Aspect aspect, int amount) {
            if (aspect != Aspect.AIR || amount != 1) return amount;
            reenter(); ++units; return 0;
        }
        @Override public boolean takeFromContainer(Aspect aspect, int amount) {
            if (aspect != Aspect.AIR || amount != 1 || units < amount) return false;
            reenter(); --units; return true;
        }
        @Override public boolean takeFromContainer(AspectList list) { return false; }
        @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return aspect == Aspect.AIR && units >= amount; }
        @Override public boolean doesContainerContain(AspectList list) { return list.getAmount(Aspect.AIR) <= units; }
        @Override public int containerContains(Aspect aspect) { return aspect == Aspect.AIR ? units : 0; }
    }
}
