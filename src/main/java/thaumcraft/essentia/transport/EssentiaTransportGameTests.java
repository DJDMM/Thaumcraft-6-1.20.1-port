package thaumcraft.essentia.transport;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.catalog.blocks.CatalogBlockEntity;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.essentia.production.SmelterBellowsBlock;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Actual registered devices, sided conservation, transient competition and reload boundaries. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EssentiaTransportGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private EssentiaTransportGameTests() {}
    private static TubeBlockEntity tube(GameTestHelper helper, BlockPos relative, String id) {
        helper.setBlock(relative, CatalogBlocks.block(id));
        return (TubeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
    private static EssentiaJarBlockEntity jar(GameTestHelper helper, BlockPos relative, Aspect filter, int amount) {
        helper.setBlock(relative, CatalogBlocks.block("jar_normal"));
        var jar = (EssentiaJarBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        if (filter != null) { CompoundTag tag = new CompoundTag(); tag.putString("AspectFilter", filter.getTag()); jar.load(tag); }
        if (amount > 0) jar.addToContainer(Aspect.AIR, amount);
        return jar;
    }
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "essentia_pipe"));
        BlockPos pos = helper.absolutePos(CENTER);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        return player;
    }
    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5), face, pos, false);
    }

    @GameTest(template = TEMPLATE)
    public static void sixOriginalIdsKeepModelsAndGetOneRegisteredTransportType(GameTestHelper helper) {
        int x = 1;
        for (String id : EssentiaTransportModule.IDS) {
            TubeBlockEntity tube = tube(helper, new BlockPos(x++, 2, 2), id);
            helper.assertTrue(tube.getType() == EssentiaTransportModule.TUBE.get() && tube.id().equals(id), "Catalogue pipe did not become transport: " + id);
            helper.assertTrue(tube.getBlockState().getBlock() instanceof TubeBlock && tube.getBlockState().getBlock().asItem() instanceof TubeBlockItem,
                    "Functional block or operating BlockItem was not installed for " + id);
            for (Direction face : Direction.values()) helper.assertTrue(tube.getBlockState().hasProperty(TubeBlock.connection(face)), "Missing original model arm property");
            helper.assertTrue(tube.getBlockState().hasProperty(TubeBlock.FACING) == (id.equals("tube_valve") || id.equals("tube_oneway")), "Original state schema changed");
        }
        helper.assertTrue(CatalogModule.ENTRIES.get("resonator").get() instanceof EssentiaResonatorItem, "Original resonator is still a placeholder");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void rawTubeStoresAndTransfersOneUnitAndRejectsInvalidCalls(GameTestHelper helper) {
        TubeBlockEntity tube = tube(helper, CENTER, "tube");
        helper.assertTrue(tube.addEssentia(Aspect.AIR, 100, Direction.UP) == 1 && tube.getEssentiaAmount(null) == 1, "Tube should accept just one unit");
        CompoundTag before = tube.saveWithoutMetadata();
        helper.assertTrue(tube.addEssentia(Aspect.FIRE, 1, Direction.NORTH) == 0 && tube.takeEssentia(Aspect.FIRE, 1, Direction.NORTH) == 0
                && tube.addEssentia(null, 1, Direction.NORTH) == 0 && tube.takeEssentia(Aspect.AIR, -1, Direction.NORTH) == 0
                && tube.takeEssentia(Aspect.AIR, 1, null) == 0 && tube.addEssentia(Aspect.AIR, 0, Direction.NORTH) == 0
                && before.equals(tube.saveWithoutMetadata()), "Rejected operation changed a tube");
        helper.assertTrue(tube.takeEssentia(Aspect.AIR, 100, Direction.UP) == 1 && tube.getEssentiaAmount(null) == 0 && tube.getEssentiaType(null) == null,
                "Tube withdrew more than one or retained the drained type");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void realJarToJarNetworkMovesEssentiaAndConservesAllHeldUnits(GameTestHelper helper) {
        var source = jar(helper, new BlockPos(2, 1, 2), null, 250);
        var sink = jar(helper, new BlockPos(6, 1, 2), Aspect.AIR, 0);
        List<TubeBlockEntity> pipes = List.of(tube(helper, new BlockPos(2, 2, 2), "tube"), tube(helper, new BlockPos(3, 2, 2), "tube"),
                tube(helper, new BlockPos(4, 2, 2), "tube"), tube(helper, new BlockPos(5, 2, 2), "tube"), tube(helper, new BlockPos(6, 2, 2), "tube"));
        helper.runAtTickTime(240, () -> {
            int held = pipes.stream().mapToInt(pipe -> pipe.getEssentiaAmount(null)).sum();
            helper.assertTrue(sink.amount() >= 10 && sink.aspect() == Aspect.AIR, "Ticking real suction network never delivered from the source jar");
            helper.assertTrue(source.amount() + sink.amount() + held == 250, "Suction network duplicated or lost an in-transit unit");
            helper.assertTrue(pipes.get(4).getSuctionAmount(null) == 63 && pipes.get(0).getSuctionAmount(null) == 59,
                    "Typed suction did not decay by one along the actual chain");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void filteredSuctionAndRestrictHalfUseOriginalComparisonAndRawAccess(GameTestHelper helper) {
        jar(helper, CENTER.below(), Aspect.AIR, 0);
        var restrict = tube(helper, CENTER, "tube_restrict"); restrict.calculateSuction();
        helper.assertTrue(restrict.getSuctionAmount(null) == 32 && restrict.getSuctionType(null) == Aspect.AIR, "Restrict did not halve original64 suction");
        var filter = (TubeFilterBlockEntity) tube(helper, CENTER, "tube_filter");
        filter.setFilter(Aspect.FIRE); filter.calculateSuction();
        helper.assertTrue(filter.getSuctionAmount(null) == 0, "Filter propagated an incompatible typed request");
        filter.setFilter(Aspect.AIR); filter.calculateSuction();
        helper.assertTrue(filter.getSuctionAmount(null) == 63 && filter.getSuctionType(null) == Aspect.AIR, "Matching filter did not propagate suction");
        helper.assertTrue(filter.addEssentia(Aspect.FIRE, 1, Direction.NORTH) == 1, "Raw TC6 API acquired an invented filter insertion gate");
        filter.calculateSuction();
        helper.assertTrue(filter.getSuctionAmount(null) == 0 && filter.getEssentiaType(null) == Aspect.FIRE,
                "Stored incompatible essentia was overwritten by a matching filter request");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void heldTypeBlocksAnIncompatibleSuctionWithoutDeletingItsUnit(GameTestHelper helper) {
        jar(helper, CENTER.below(), Aspect.FIRE, 0);
        var pipe = tube(helper, CENTER, "tube"); pipe.addEssentia(Aspect.AIR, 1, Direction.UP); pipe.calculateSuction();
        helper.assertTrue(pipe.getSuctionAmount(null) == 0 && pipe.getEssentiaType(null) == Aspect.AIR && pipe.getEssentiaAmount(null) == 1,
                "Pipe adopted suction of a different aspect while holding one unit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void rivalSuctionVentsFortyTicksButDoesNotConsumeEssentiaOrFlux(GameTestHelper helper) {
        var pipe = tube(helper, CENTER, "tube");
        TubeBlockEntity left = tube(helper, CENTER.west(), "tube"), right = tube(helper, CENTER.east(), "tube");
        left.setSuction(Aspect.AIR, 63); right.setSuction(Aspect.FIRE, 62);
        pipe.calculateSuction();
        float before = AuraManager.getFlux(helper.getLevel(), pipe.getBlockPos());
        pipe.checkVenting();
        helper.assertTrue(pipe.getSuctionType(null) == Aspect.AIR && pipe.getSuctionAmount(null) == 62 && pipe.ventingTicks() == 40,
                "Adjacent equal-strength different suction did not begin original40 tick contention");
        pipe.addEssentia(Aspect.AIR, 1, Direction.UP);
        for (int i = 0; i < 39; i++) pipe.tick();
        helper.assertTrue(pipe.ventingTicks() == 1 && pipe.getEssentiaAmount(null) == 1 && AuraManager.getFlux(helper.getLevel(), pipe.getBlockPos()) == before,
                "Venting destroyed the held unit, polluted aura or lasted less than40 ticks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void suctionCycleLosesGhostDemandAndNeverCreatesEssentia(GameTestHelper helper) {
        List<TubeBlockEntity> ring = List.of(tube(helper, new BlockPos(3, 2, 3), "tube"), tube(helper, new BlockPos(4, 2, 3), "tube"),
                tube(helper, new BlockPos(4, 2, 4), "tube"), tube(helper, new BlockPos(3, 2, 4), "tube"));
        ring.forEach(pipe -> pipe.setSuction(Aspect.AIR, 64));
        for (int step = 0; step < 80; step++) ring.forEach(TubeBlockEntity::calculateSuction);
        helper.assertTrue(ring.stream().allMatch(pipe -> pipe.getSuctionAmount(null) == 0 && pipe.getSuctionType(null) == null && pipe.getEssentiaAmount(null) == 0),
                "Closed cycle retained an unanchored demand or created essentia");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void casterSideToggleClosesBothEndsAndConnectionModels(GameTestHelper helper) {
        TubeBlockEntity pipe = tube(helper, CENTER, "tube"), next = tube(helper, CENTER.east(), "tube_filter");
        pipe.refreshConnections(); next.refreshConnections();
        helper.assertTrue(pipe.getBlockState().getValue(TubeBlock.connection(Direction.EAST)) && next.getBlockState().getValue(TubeBlock.connection(Direction.WEST)), "Model did not connect registered transport devices");
        ServerPlayer player = player(helper); player.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("caster_basic"));
        BlockHitResult hit = hit(pipe.getBlockPos(), Direction.EAST);
        helper.assertTrue(pipe.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit).consumesAction(), "Caster arm interaction did not consume click");
        helper.assertTrue(!pipe.sideOpen(Direction.EAST) && !next.sideOpen(Direction.WEST)
                && !pipe.getBlockState().getValue(TubeBlock.connection(Direction.EAST)) && !next.getBlockState().getValue(TubeBlock.connection(Direction.WEST))
                && EssentiaTransportModule.neighbor(helper.getLevel(), pipe.getBlockPos(), Direction.EAST) == null, "One end/model still connected after arm closure");
        pipe.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(pipe.sideOpen(Direction.EAST) && next.sideOpen(Direction.WEST), "Closed invisible arm could not be reopened");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void neighborQueriesDoNotLoadMissingChunksOrConnectVanillaBlocks(GameTestHelper helper) {
        var pipe = tube(helper, CENTER, "tube"); helper.setBlock(CENTER.east(), Blocks.STONE);
        helper.assertTrue(EssentiaTransportModule.neighbor(helper.getLevel(), pipe.getBlockPos(), Direction.EAST) == null, "Pipe connected a non-transport block");
        BlockPos distant = new BlockPos(2_100_000, 70, 2_100_000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(distant), "Unloaded lookup fixture unexpectedly exists");
        for (Direction face : Direction.values()) helper.assertTrue(EssentiaTransportModule.neighbor(helper.getLevel(), distant, face) == null
                && !helper.getLevel().hasChunkAt(distant.relative(face)), "Transport lookup loaded a missing neighbor chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void valveClosesOnPowerEdgeAndReopensOnFallingEdge(GameTestHelper helper) {
        var valve = tube(helper, CENTER, "tube_valve");
        jar(helper, CENTER.below(), Aspect.AIR, 0); valve.setFacing(Direction.NORTH); valve.calculateSuction();
        helper.assertTrue(valve.allowFlow() && valve.getSuctionAmount(null) == 63 && !valve.isConnectable(Direction.NORTH), "Valve default/handle mask was wrong");
        helper.setBlock(CENTER.south(), Blocks.REDSTONE_BLOCK);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(!valve.allowFlow() && valve.getSuctionAmount(null) == 0, "Rising redstone edge did not close suction");
            valve.toggleFlow();
            helper.assertTrue(valve.allowFlow(), "Manual control under existing redstone was ignored");
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(valve.allowFlow(), "Constant power overrode original edge-triggered manual state");
            valve.toggleFlow(); helper.setBlock(CENTER.south(), Blocks.AIR);
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(valve.allowFlow() && valve.getSuctionAmount(null) == 63, "Falling redstone edge did not reopen valve");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void oneWayUsesOriginalOppositeFacingDemandAndExcludesThatInput(GameTestHelper helper) {
        jar(helper, CENTER.below(), Aspect.AIR, 0);
        var valve = tube(helper, CENTER, "tube_oneway");
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER.north(), "tube_buffer"); buffer.addToContainer(Aspect.AIR, 1);
        valve.setFacing(Direction.UP); valve.calculateSuction(); valve.equalize();
        helper.assertTrue(valve.getSuctionAmount(null) == 63 && valve.getEssentiaAmount(null) == 1 && buffer.getEssentiaAmount(null) == 0,
                "One-way propagation/allowed source direction differs from BETA26");
        valve.takeEssentia(Aspect.AIR, 1, Direction.UP); buffer.addToContainer(Aspect.AIR, 1);
        valve.setFacing(Direction.DOWN); valve.calculateSuction(); valve.equalize();
        helper.assertTrue(valve.getSuctionAmount(null) == 0 && valve.getEssentiaAmount(null) == 0 && buffer.getEssentiaAmount(null) == 1,
                "Reversed one-way transported without its facing-specific demand");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bufferMixedCapacityIsTenDetachedAndReportsOriginalComparator(GameTestHelper helper) {
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer");
        for (int i = 0; i < 5; i++) { buffer.addToContainer(Aspect.AIR, 1); buffer.addToContainer(Aspect.FIRE, 1); }
        helper.assertTrue(buffer.getEssentiaAmount(null) == 10 && buffer.containerContains(Aspect.AIR) == 5 && buffer.containerContains(Aspect.FIRE) == 5
                && buffer.addToContainer(Aspect.WATER, 1) == 1 && buffer.addToContainer(Aspect.AIR, 2) == 2
                && buffer.getBlockState().getAnalogOutputSignal(helper.getLevel(), buffer.getBlockPos()) == 15, "Mixed buffer exceeded capacity or accepted original forbidden batch insertion");
        AspectList view = buffer.getAspects(); view.remove(Aspect.AIR); view.add(Aspect.WATER, 50);
        helper.assertTrue(buffer.getEssentiaAmount(null) == 10 && buffer.containerContains(Aspect.WATER) == 0, "Container view mutated the server buffer");
        helper.assertTrue(buffer.takeFromContainer(Aspect.AIR, 5) && buffer.takeFromContainer(Aspect.FIRE, 5)
                && buffer.getBlockState().getAnalogOutputSignal(helper.getLevel(), buffer.getBlockPos()) == 0, "Buffer did not drain/comparator did not reset");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bufferReservesTypeForStrongerCompatibleNeighbor(GameTestHelper helper) {
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer");
        TubeBlockEntity weak = tube(helper, CENTER.west(), "tube"), strong = tube(helper, CENTER.east(), "tube");
        weak.setSuction(Aspect.AIR, 31); strong.setSuction(Aspect.AIR, 63); buffer.addToContainer(Aspect.AIR, 1);
        helper.assertTrue(buffer.takeEssentia(Aspect.AIR, 1, Direction.WEST) == 0 && buffer.getEssentiaAmount(null) == 1, "Weaker consumer stole a unit reserved by stronger demand");
        helper.assertTrue(buffer.takeEssentia(Aspect.AIR, 1, Direction.EAST) == 1 && buffer.getEssentiaAmount(null) == 0, "Stronger compatible consumer was denied");
        buffer.addToContainer(Aspect.FIRE, 1);
        helper.assertTrue(buffer.takeEssentia(Aspect.FIRE, 1, Direction.WEST) == 1, "Unrelated strong aspect demand reserved the wrong type");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void directedEnabledBellowsBoostBufferAndChokeSetsOneOrZero(GameTestHelper helper) {
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer");
        helper.setBlock(CENTER.north(), CatalogBlocks.block("bellows").defaultBlockState().setValue(SmelterBellowsBlock.FACING, Direction.SOUTH));
        helper.setBlock(CENTER.south(), CatalogBlocks.block("bellows").defaultBlockState().setValue(SmelterBellowsBlock.FACING, Direction.SOUTH));
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(buffer.bellowsCount() == 1 && buffer.getSuctionAmount(Direction.EAST) == 32, "Buffer counted wrong-direction bellows or failed original32 suction");
            buffer.cycleChoke(Direction.EAST);
            helper.assertTrue(buffer.choke(Direction.EAST) == 1 && buffer.getSuctionAmount(Direction.EAST) == 1, "Blue choke did not reduce suction to1");
            buffer.cycleChoke(Direction.EAST);
            helper.assertTrue(buffer.choke(Direction.EAST) == 2 && buffer.getSuctionAmount(Direction.EAST) == 0 && buffer.canOutputTo(Direction.EAST), "Red choke should disable suction, not raw output");
            helper.setBlock(CENTER.north().above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(buffer.bellowsCount() == 0 && buffer.getSuctionAmount(Direction.WEST) == 1, "Powered bellows continued supplying suction");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void bufferPullsOneUnitPerFiveTicksFromRealAlembic(GameTestHelper helper) {
        helper.setBlock(CENTER.west(), CatalogBlocks.block("alembic"));
        var source = (AlembicBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CENTER.west())); source.addExact(Aspect.AIR, 20);
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer");
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(buffer.getEssentiaAmount(null) == 10 && buffer.containerContains(Aspect.AIR) == 10 && source.amount() == 10,
                    "Buffer/alembic fill exceeded ten units, duplicated or failed actual extraction");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void labelsAndCasterBufferChokesUseRealPlayerActionsWithoutItemPayment(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var filter = (TubeFilterBlockEntity) tube(helper, CENTER, "tube_filter");
        ItemStack sample = CatalogModule.aspectStack("phial_filled", Aspect.FIRE, 10), sampleBefore = sample.copy(); player.setItemInHand(InteractionHand.MAIN_HAND, sample);
        filter.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(filter.getBlockPos(), Direction.UP));
        helper.assertTrue(filter.filter() == Aspect.FIRE && ItemStack.matches(sampleBefore, player.getMainHandItem()) && sample.getCount() == 1,
                "Filter template consumed the source phial or selected the wrong aspect");
        player.setShiftKeyDown(true);
        filter.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(filter.getBlockPos(), Direction.UP));
        helper.assertTrue(filter.filter() == null, "Sneaking click did not clear filter");
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer"); tube(helper, CENTER.east(), "tube");
        player.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("caster_basic"));
        buffer.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(buffer.getBlockPos(), Direction.EAST));
        helper.assertTrue(buffer.choke(Direction.EAST) == 1 && buffer.sideOpen(Direction.EAST), "Sneaking caster changed connectivity instead of choke");
        CompoundTag before = buffer.saveWithoutMetadata();
        player.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("resonator"));
        InteractionResult result = player.getMainHandItem().onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(buffer.getBlockPos(), Direction.EAST)));
        helper.assertTrue(result.consumesAction() && before.equals(buffer.saveWithoutMetadata()), "Resonator mutated the network it inspected");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void allTubeVariantsKeepContentsSuctionAndControlsAcrossBlockEntityReload(GameTestHelper helper) {
        int x = 1;
        for (String id : EssentiaTransportModule.IDS) {
            var pipe = tube(helper, new BlockPos(x++, 2, 3), id); pipe.setFacing(Direction.UP); pipe.toggleSide(Direction.NORTH); pipe.setSuction(Aspect.AIR, 29);
            if (pipe instanceof TubeBufferBlockEntity buffer) { buffer.addToContainer(Aspect.AIR, 1); buffer.addToContainer(Aspect.FIRE, 1); buffer.cycleChoke(Direction.EAST); }
            else pipe.addEssentia(Aspect.AIR, 1, Direction.WEST);
            if (pipe instanceof TubeFilterBlockEntity filter) filter.setFilter(Aspect.FIRE);
            if (id.equals("tube_valve")) pipe.toggleFlow();
            CompoundTag saved = pipe.saveWithoutMetadata();
            TubeBlockEntity loaded = TubeBlockEntity.create(pipe.getBlockPos(), pipe.getBlockState()); loaded.load(saved);
            helper.assertTrue(saved.equals(loaded.saveWithoutMetadata()) && loaded.getUpdatePacket() != null,
                    "Variant lost persisted/server-synchronized data: " + id);
            if (loaded instanceof TubeBufferBlockEntity buffer) helper.assertTrue(buffer.getAspects().visSize() == 2 && buffer.choke(Direction.EAST) == 1, "Mixed buffer state lost on reload");
        }
        for (String id : List.of("tube_valve", "tube_oneway")) {
            var state = CatalogBlocks.block(id).defaultBlockState().setValue(TubeBlock.FACING, Direction.EAST);
            TubeBlockEntity migrated = TubeBlockEntity.create(helper.absolutePos(CENTER), state);
            migrated.load(new CompoundTag());
            helper.assertTrue(migrated.facing() == Direction.EAST && migrated.allowFlow() && migrated.getEssentiaAmount(null) == 0,
                    "Empty legacy visual data discarded the blockstate's pipe orientation: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void invalidSavedTubeDataIsBoundedAndBreakingPollutesOnlyHeldUnits(GameTestHelper helper) {
        var pipe = tube(helper, CENTER, "tube");
        CompoundTag tag = new CompoundTag(); tag.putString("type", "aer"); tag.putInt("amount", Integer.MAX_VALUE);
        tag.putInt("side", -100); tag.putInt("samount", -20); tag.putByteArray("open", new byte[]{1}); pipe.load(tag);
        helper.assertTrue(pipe.getEssentiaAmount(null) == 1 && pipe.getSuctionAmount(null) == 0 && pipe.facing() == Direction.DOWN && pipe.sideOpen(Direction.UP),
                "Malformed tube state escaped bounds");
        float before = AuraManager.getFlux(helper.getLevel(), pipe.getBlockPos()); helper.setBlock(CENTER, Blocks.AIR);
        helper.assertTrue(AuraManager.getFlux(helper.getLevel(), pipe.getBlockPos()) == before + 1, "Breaking pipe did not release exactly its single held unit as flux");
        var buffer = (TubeBufferBlockEntity) tube(helper, CENTER, "tube_buffer");
        for (int i = 0; i < 7; i++) buffer.addToContainer(Aspect.AIR, 1);
        before = AuraManager.getFlux(helper.getLevel(), buffer.getBlockPos()); helper.setBlock(CENTER, Blocks.AIR);
        helper.assertTrue(AuraManager.getFlux(helper.getLevel(), buffer.getBlockPos()) == before + 7, "Breaking buffer retained or duplicated mixed storage");
        helper.succeed();
    }

    private static BlockEntity installLegacyVisual(GameTestHelper helper, String id, BlockPos relative, Direction facing) {
        BlockPos pos = helper.absolutePos(relative);
        var state = CatalogBlocks.block(id).defaultBlockState().setValue(TubeBlock.FACING, facing);
        helper.getLevel().setBlock(pos, state, 3);
        // This is the actual pre-0.13 save format, loaded through the vanilla BE type registry.
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", "thaumcraft:catalog_visual");
        legacy.putInt("x", pos.getX()); legacy.putInt("y", pos.getY()); legacy.putInt("z", pos.getZ());
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, legacy);
        helper.assertTrue(loaded instanceof CatalogBlockEntity && loaded.getType() == CatalogBlocks.VISUAL_TILE.get(),
                "Original visual BE save did not load through its actual legacy type");
        helper.getLevel().setBlockEntity(loaded);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == loaded, "Legacy save was not installed as the real chunk BE");
        return loaded;
    }

    @GameTest(template = TEMPLATE)
    public static void legacyVisualNbtMigratesOnlyValveAndOneWayAtServerEndTick(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockEntity oldValve = installLegacyVisual(helper, "tube_valve", CENTER, Direction.WEST);
        BlockEntity oldOneWay = installLegacyVisual(helper, "tube_oneway", CENTER.east(), Direction.UP);
        // An unrelated catalogue anchor and an already functional pipe must retain their identity.
        helper.setBlock(CENTER.south(), CatalogBlocks.block("centrifuge"));
        BlockEntity centrifuge = level.getBlockEntity(helper.absolutePos(CENTER.south()));
        TubeBlockEntity ordinary = tube(helper, CENTER.north(), "tube"); ordinary.addEssentia(Aspect.FIRE, 1, Direction.UP);
        float before = AuraManager.getFlux(level, oldValve.getBlockPos());
        LevelChunk chunk = level.getChunkSource().getChunkNow(oldValve.getBlockPos().getX() >> 4, oldValve.getBlockPos().getZ() >> 4);
        var otherChunk = level.getChunkSource().getChunkNow(oldOneWay.getBlockPos().getX() >> 4, oldOneWay.getBlockPos().getZ() >> 4);
        chunk.setUnsaved(false); otherChunk.setUnsaved(false);
        LegacyTubeMigration.onChunkLoad(new ChunkEvent.Load(chunk, false));
        LegacyTubeMigration.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.START, level, () -> true));
        helper.assertTrue(level.getBlockEntity(oldValve.getBlockPos()) == oldValve && level.getBlockEntity(oldOneWay.getBlockPos()) == oldOneWay,
                "Migration mutated a chunk during its Load callback or the START phase");
        // The test structure can straddle chunks; each actual loaded chunk is queued independently.
        LegacyTubeMigration.onChunkLoad(new ChunkEvent.Load(otherChunk, false));
        // Other full-suite chunks may precede these in the shared 64-chunk queue. Exercise
        // bounded END callbacks without ticking neighboring pipes or weakening the state checks.
        for(int attempts=0;attempts<200&&(!(level.getBlockEntity(oldValve.getBlockPos()) instanceof TubeBlockEntity)
                ||!(level.getBlockEntity(oldOneWay.getBlockPos()) instanceof TubeBlockEntity));attempts++)
            LegacyTubeMigration.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
        helper.assertTrue(level.getBlockEntity(oldValve.getBlockPos()) instanceof TubeBlockEntity
                &&level.getBlockEntity(oldOneWay.getBlockPos()) instanceof TubeBlockEntity,"Queued legacy chunks were not migrated within the bounded wait");
        var valve = (TubeBlockEntity) level.getBlockEntity(oldValve.getBlockPos());
        var oneWay = (TubeBlockEntity) level.getBlockEntity(oldOneWay.getBlockPos());
        helper.assertTrue(valve.getType() == EssentiaTransportModule.TUBE.get() && valve.facing() == Direction.WEST && valve.allowFlow()
                && valve.getEssentiaAmount(null) == 0 && oneWay.facing() == Direction.UP && oneWay.getEssentiaAmount(null) == 0,
                "Legacy migration lost orientation, failed actual type replacement or invented contents");
        helper.assertTrue(oldValve.isRemoved() && oldOneWay.isRemoved() && chunk.isUnsaved()
                && level.getBlockEntity(ordinary.getBlockPos()) == ordinary && ordinary.getEssentiaType(null) == Aspect.FIRE
                && level.getBlockEntity(centrifuge.getBlockPos()) == centrifuge && AuraManager.getFlux(level, oldValve.getBlockPos()) == before,
                "Migration failed persistence or changed unrelated BEs, held essence or aura");
        CompoundTag savedValve = chunk.getBlockEntityNbtForSaving(valve.getBlockPos());
        CompoundTag savedOneWay = otherChunk.getBlockEntityNbtForSaving(oneWay.getBlockPos());
        helper.assertTrue(savedValve != null && savedOneWay != null && savedValve.getString("id").equals("thaumcraft:essentia_tube")
                && savedOneWay.getString("id").equals("thaumcraft:essentia_tube")
                && BlockEntity.loadStatic(valve.getBlockPos(), valve.getBlockState(), savedValve) instanceof TubeBlockEntity
                && BlockEntity.loadStatic(oneWay.getBlockPos(), oneWay.getBlockState(), savedOneWay) instanceof TubeBlockEntity
                && valve.getUpdatePacket().getType() == EssentiaTransportModule.TUBE.get()
                && oneWay.getUpdatePacket().getType() == EssentiaTransportModule.TUBE.get(),
                "Repaired chunk save or update packet restored the old catalogue type");
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(oldValve.getBlockPos())).isEmpty(),
                "Migration emitted block loot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void workerChunkLoadDefersRepairAndInstallsWorkingTickerWithoutForcedChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockEntity legacy = installLegacyVisual(helper, "tube_oneway", CENTER, Direction.DOWN);
        jar(helper, CENTER.below(), Aspect.AIR, 0);
        LevelChunk chunk = level.getChunkSource().getChunkNow(legacy.getBlockPos().getX() >> 4, legacy.getBlockPos().getZ() >> 4);
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { LegacyTubeMigration.onChunkLoad(new ChunkEvent.Load(chunk, false)); }
            catch (Throwable error) { failure.set(error); }
        }, "tc6-legacy-chunk-load-test");
        worker.start();
        try { worker.join(5_000); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        helper.assertTrue(!worker.isAlive() && failure.get() == null && level.getBlockEntity(legacy.getBlockPos()) == legacy,
                "Worker chunk callback performed server-world mutation or blocked: " + failure.get());
        BlockPos unloaded = new BlockPos(2_300_000, 70, 2_300_000);
        helper.assertTrue(!level.hasChunkAt(unloaded), "Unloaded-chunk fixture unexpectedly exists");
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(level.getBlockEntity(legacy.getBlockPos()) instanceof TubeBlockEntity,
                    "Actual registered END-tick event never repaired the queued legacy BE");
            TubeBlockEntity pipe = (TubeBlockEntity) level.getBlockEntity(legacy.getBlockPos());
            helper.assertTrue(pipe.facing() == Direction.DOWN && pipe.getBlockState().getValue(TubeBlock.FACING) == Direction.DOWN
                    && pipe.getEssentiaAmount(null) == 0 && legacy.isRemoved() && !level.hasChunkAt(unloaded),
                    "Deferred migration lost facing, invented essence or loaded an unrelated chunk");
        });
        helper.runAtTickTime(20, () -> {
            TubeBlockEntity pipe = (TubeBlockEntity) level.getBlockEntity(legacy.getBlockPos());
            // Facing DOWN samples UP, so the labelled jar below must not induce suction.
            helper.assertTrue(pipe.getSuctionAmount(null) == 0, "Migrated one-way direction was not obeyed by its real ticker");
            pipe.setFacing(Direction.UP);
        });
        helper.runAtTickTime(35, () -> {
            TubeBlockEntity pipe = (TubeBlockEntity) level.getBlockEntity(legacy.getBlockPos());
            helper.assertTrue(pipe.getSuctionAmount(null) == 63 && pipe.getSuctionType(null) == Aspect.AIR,
                    "Migrated BE was replaced without registering a functional server ticker");
            helper.succeed();
        });
    }
}
