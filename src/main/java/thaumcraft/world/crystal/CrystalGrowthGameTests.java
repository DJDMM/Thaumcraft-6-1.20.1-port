package thaumcraft.world.crystal;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real registered cluster states, native random ticks, paid aura and survival/Forge boundaries. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CrystalGrowthGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private static final String[] TYPES = {"aer", "ignis", "aqua", "terra", "ordo", "perditio", "vitium"};
    private CrystalGrowthGameTests() {}
    private static PrimalCrystalClusterBlock crystal(String aspect) {
        return (PrimalCrystalClusterBlock)(aspect.equals("vitium") ? CatalogBlocks.block("crystal_vitium")
                : WorldModule.CRYSTALS.get(aspect).get());
    }
    private static BlockPos origin(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            for (int y = 0; y <= 1; y++) h.getLevel().setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        return pos;
    }
    private static BlockState plant(GameTestHelper h, BlockPos pos, String type, int size, int gen) {
        var state = crystal(type).defaultBlockState().setValue(PrimalCrystalClusterBlock.SIZE, size)
                .setValue(PrimalCrystalClusterBlock.GENERATION, gen);
        h.getLevel().setBlockAndUpdate(pos, state);
        return state;
    }
    private static void aura(GameTestHelper h, BlockPos pos, float vis, float flux) {
        AuraManager.drainVis(h.getLevel(), pos, Float.MAX_VALUE, false);
        AuraManager.drainFlux(h.getLevel(), pos, Float.MAX_VALUE, false);
        AuraManager.addVis(h.getLevel(), pos, vis);
        AuraManager.addFlux(h.getLevel(), pos, flux);
    }
    private static RandomSource roll(int generation, boolean active, boolean preserveGeneration) {
        for (long seed = 0; seed < 100000; seed++) {
            var probe = RandomSource.create(seed);
            boolean matches = (probe.nextInt(3 + generation) == 0) == active;
            if (matches && (!active || (probe.nextInt(6) == 0) == preserveGeneration)) return RandomSource.create(seed);
        }
        throw new AssertionError("No deterministic activation seed");
    }
    private static void tick(GameTestHelper h, BlockPos pos, boolean active) {
        var state = h.getLevel().getBlockState(pos);
        ((PrimalCrystalClusterBlock)state.getBlock()).randomTick(state, h.getLevel(), pos,
                roll(state.getValue(PrimalCrystalClusterBlock.GENERATION), active, false));
    }
    private static long spreadSeed(int dx, int dy, int dz, boolean succeed) {
        for (long seed = 0; seed < 1000000; seed++) {
            var random = RandomSource.create(seed);
            if (random.nextInt(3) - 1 == dx && random.nextInt(3) - 1 == dy && random.nextInt(3) - 1 == dz
                    && ((random.nextInt(16) == 0) == succeed)) return seed;
        }
        throw new AssertionError("No deterministic spread seed");
    }
    private static void spread(GameTestHelper h, BlockPos pos, boolean preserveGeneration) {
        h.getLevel().random.setSeed(spreadSeed(1, 0, 0, true));
        var state = h.getLevel().getBlockState(pos);
        crystal(((PrimalCrystalClusterBlock)state.getBlock()).aspect().getTag()).randomTick(state, h.getLevel(), pos,
                roll(state.getValue(PrimalCrystalClusterBlock.GENERATION), true, preserveGeneration));
    }
    private static int size(GameTestHelper h, BlockPos pos) { return h.getLevel().getBlockState(pos).getValue(PrimalCrystalClusterBlock.SIZE); }
    private static int drops(GameTestHelper h, BlockPos pos, Aspect type) {
        return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2))
                .stream().map(net.minecraft.world.entity.item.ItemEntity::getItem)
                .filter(item -> AspectCrystalItem.crystalAspect(item) == type).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = TEMPLATE) public static void allSevenRegisteredClustersHaveOriginalProperties(GameTestHelper h) {
        var pos = origin(h);
        for (String type : TYPES) {
            var block = crystal(type); var state = plant(h, pos, type, 0, 1);
            h.assertTrue(block.aspect() == Aspect.getAspect(type) && state.getValue(PrimalCrystalClusterBlock.SIZE) == 0
                    && state.getValue(PrimalCrystalClusterBlock.GENERATION) == 1 && state.isRandomlyTicking(), "Original default/random ticking lost: " + type);
            h.assertTrue(state.getLightEmission() == 1 && state.getDestroySpeed(h.getLevel(), pos) == .25F
                    && block.getExplosionResistance() == .25F && !state.requiresCorrectToolForDrops()
                    && state.getCollisionShape(h.getLevel(), pos).isEmpty(), "Original physical values lost: " + type);
            for (Direction dir : Direction.values()) h.assertTrue(!state.isFaceSturdy(h.getLevel(), pos, dir), "Crystal supports another block");
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void activationRollUsesThreePlusGenerationAndNeverDebitsOnMiss(GameTestHelper h) {
        var pos = origin(h);
        for (int gen = 1; gen <= 4; gen++) {
            plant(h, pos, "aer", 0, gen); aura(h, pos, 1000, 12);
            tick(h, pos, false);
            h.assertTrue(size(h, pos) == 0 && AuraManager.getVis(h.getLevel(), pos) == 1000,
                    "Missed activation changed state or aura at generation " + gen);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void everyPrimalGrowsExactlyOneAndPaysTenVisOnly(GameTestHelper h) {
        var pos = origin(h);
        for (String type : Arrays.copyOf(TYPES, 6)) {
            plant(h, pos, type, 0, 1); aura(h, pos, 1000, 33); tick(h, pos, true);
            h.assertTrue(size(h, pos) == 1 && AuraManager.getVis(h.getLevel(), pos) == 990
                    && AuraManager.getFlux(h.getLevel(), pos) == 33, "Primal growth was free or polluted: " + type);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void fluxGrowthPaysTenFluxAndDoesNotReadVis(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "vitium", 0, 1); aura(h, pos, 2, 1000); tick(h, pos, true);
        h.assertTrue(size(h, pos) == 1 && AuraManager.getFlux(h.getLevel(), pos) == 990
                && AuraManager.getVis(h.getLevel(), pos) == 2, "Flux cluster consumed/refused according to vis"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void growthStrictlyRequiresMoreThanBasePlusTen(GameTestHelper h) {
        var pos = origin(h); int base = AuraManager.getAuraBase(h.getLevel(), pos);
        for (String type : new String[]{"aer", "vitium"}) {
            plant(h, pos, type, 0, 1); aura(h, pos, base + 10, base + 10); tick(h, pos, true);
            h.assertTrue(size(h, pos) == 0, "Equality at base+10 grew a crystal");
            aura(h, pos, base + 10.5F, base + 10.5F); tick(h, pos, true);
            h.assertTrue(size(h, pos) == 1 && (type.equals("vitium") ? AuraManager.getFlux(h.getLevel(), pos)
                    : AuraManager.getVis(h.getLevel(), pos)) == base + .5F, "Strict fractional threshold/payment was rounded");
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void primalDecayAtTenOrBelowReturnsTenWithoutDrops(GameTestHelper h) {
        var pos = origin(h);
        for (float vis : new float[]{0, 10}) {
            plant(h, pos, "aer", 2, 3); aura(h, pos, vis, 88); tick(h, pos, true);
            h.assertTrue(size(h, pos) == 1 && AuraManager.getVis(h.getLevel(), pos) == vis + 10
                    && AuraManager.getFlux(h.getLevel(), pos) == 88 && drops(h, pos, Aspect.AIR) == 0,
                    "Decay lost its ten-vis return or produced loot");
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void fluxDecayReturnsOnlyFlux(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "vitium", 3, 4); aura(h, pos, 1000, 10); tick(h, pos, true);
        h.assertTrue(size(h, pos) == 2 && AuraManager.getFlux(h.getLevel(), pos) == 20
                && AuraManager.getVis(h.getLevel(), pos) == 1000, "Flux decay returned vis or wrong amount"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void isolatedSmallestClusterSurvivesAuraExhaustion(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 0, 4); aura(h, pos, 0, 0); tick(h, pos, true);
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) && size(h, pos) == 0
                && AuraManager.getVis(h.getLevel(), pos) == 0, "Isolated last seed disappeared or generated aura"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void exhaustedSmallClusterTouchingSameAspectDisappearsAndReturnsTen(GameTestHelper h) {
        var pos = origin(h); plant(h, pos.east(), "aer", 2, 1); plant(h, pos, "aer", 0, 1);
        aura(h, pos, 0, 0); tick(h, pos, true);
        h.assertTrue(h.getLevel().isEmptyBlock(pos) && h.getLevel().getBlockState(pos.east()).is(crystal("aer"))
                && AuraManager.getVis(h.getLevel(), pos) == 10 && drops(h, pos, Aspect.AIR) == 0,
                "Natural small-cluster degeneration duplicated loot or failed ten-vis return"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void fluxSmallClusterRemovalReturnsTenPollution(GameTestHelper h) {
        var pos = origin(h); plant(h, pos.east(), "vitium", 1, 1); plant(h, pos, "vitium", 0, 1);
        aura(h, pos, 55, 0); tick(h, pos, true);
        h.assertTrue(h.getLevel().isEmptyBlock(pos) && AuraManager.getFlux(h.getLevel(), pos) == 10
                && AuraManager.getVis(h.getLevel(), pos) == 55 && drops(h, pos, Aspect.FLUX) == 0, "Flux removal changed vis or dropped material"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void DifferentAspectAndDiagonalDoNotDeleteTheLastSeed(GameTestHelper h) {
        var pos = origin(h); plant(h, pos.east(), "ignis", 1, 1); plant(h, pos.offset(1, 0, 1), "aer", 1, 1);
        plant(h, pos, "aer", 0, 1); aura(h, pos, 0, 0); tick(h, pos, true);
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) && AuraManager.getVis(h.getLevel(), pos) == 0,
                "Diagonal/foreign-aspect crystal counted as an adjacent identical seed"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void signedLegacyPositionControlsCapsInsteadOfModernPacking(GameTestHelper h) {
        for (BlockPos pos : new BlockPos[]{new BlockPos(1, 72, 2), new BlockPos(-7, 100, 4), new BlockPos(-1, -30, -2)}) {
            long expected = ((long)pos.getX() & 67108863L) << 38 | ((long)pos.getY() & 4095L) << 26 | ((long)pos.getZ() & 67108863L);
            h.assertTrue(PrimalCrystalClusterBlock.originalPositionLong(pos) == expected
                    && PrimalCrystalClusterBlock.growthLimit(pos, 4) == 1 + expected % 3,
                    "Original signed 1.12 position modulo was changed");
        }
        h.assertTrue(PrimalCrystalClusterBlock.originalPositionLong(new BlockPos(1, 72, 2))
                != new BlockPos(1, 72, 2).asLong(), "Fixture did not distinguish packing versions"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void fourthGenerationCanNeitherExceedItsCapNorSpread(GameTestHelper h) {
        var pos = origin(h); int cap = Math.max(0, Math.min(3, (int)PrimalCrystalClusterBlock.growthLimit(pos, 4)));
        plant(h, pos, "aer", cap, 4); aura(h, pos, 1000, 0); spread(h, pos, false);
        h.assertTrue(size(h, pos) == cap && h.getLevel().isEmptyBlock(pos.east())
                && AuraManager.getVis(h.getLevel(), pos) == 1000, "Fourth generation spread or paid above cap"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void successfulSpreadPaysTenAndCreatesSmallerNextGeneration(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 1); aura(h, pos, 1000, 66); spread(h, pos, false);
        var child = h.getLevel().getBlockState(pos.east());
        h.assertTrue(child.is(crystal("aer")) && child.getValue(PrimalCrystalClusterBlock.SIZE) == 0
                && child.getValue(PrimalCrystalClusterBlock.GENERATION) == 2 && size(h, pos) == 3
                && AuraManager.getVis(h.getLevel(), pos) == 990 && AuraManager.getFlux(h.getLevel(), pos) == 66,
                "Spread did not pay exactly ten for one SIZE0 child of next generation"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void oneInSixSpreadKeepsItsParentGeneration(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 3); aura(h, pos, 1000, 0); spread(h, pos, true);
        var child = h.getLevel().getBlockState(pos.east());
        h.assertTrue(child.is(crystal("aer")) && child.getValue(PrimalCrystalClusterBlock.GENERATION) == 3
                && AuraManager.getVis(h.getLevel(), pos) == 990, "Generation preservation roll became generation2/4 or free"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void fluxSpreadPaysPollutionAndRetainsPrimalAura(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "vitium", 3, 2); aura(h, pos, 1, 1000); spread(h, pos, false);
        var child = h.getLevel().getBlockState(pos.east());
        h.assertTrue(child.is(crystal("vitium")) && child.getValue(PrimalCrystalClusterBlock.GENERATION) == 3
                && AuraManager.getFlux(h.getLevel(), pos) == 990 && AuraManager.getVis(h.getLevel(), pos) == 1,
                "Flux spread consulted/debited vis"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void failedOneInSixteenSpreadNeverPays(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 1); aura(h, pos, 1000, 0);
        h.getLevel().random.setSeed(spreadSeed(1, 0, 0, false)); tick(h, pos, true);
        h.assertTrue(h.getLevel().isEmptyBlock(pos.east()) && AuraManager.getVis(h.getLevel(), pos) == 1000,
                "Rejected 1-in-16 spread paid or planted"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void liquidAndSolidTargetsAreNotReplacedAndCostNothing(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 1);
        for (var obstruction : new BlockState[]{Blocks.WATER.defaultBlockState(), Blocks.STONE.defaultBlockState()}) {
            h.getLevel().setBlockAndUpdate(pos.east(), obstruction); aura(h, pos, 1000, 0); spread(h, pos, false);
            h.assertTrue(h.getLevel().getBlockState(pos.east()).equals(obstruction)
                    && AuraManager.getVis(h.getLevel(), pos) == 1000, "Spread replaced a solid/liquid target");
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void spreadCanReplaceDryVegetationOnOriginalRockSupport(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 1);
        h.getLevel().setBlock(pos.east(), Blocks.GRASS.defaultBlockState(), 2);
        aura(h, pos, 1000, 0); spread(h, pos, false);
        h.assertTrue(h.getLevel().getBlockState(pos.east()).is(crystal("aer"))
                && AuraManager.getVis(h.getLevel(), pos) == 990, "Dry replaceable vegetation refused a supported paid spread"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void allSixRockSupportsPlaceAndSurviveIndependentOfSavedFacing(GameTestHelper h) {
        var pos = origin(h); var player = player(h, pos); var block = crystal("aer");
        for (Direction support : Direction.values()) {
            for (Direction dir : Direction.values()) h.getLevel().setBlockAndUpdate(pos.relative(dir), Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos.relative(support), Blocks.STONE.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.relative(support)), support.getOpposite(), pos.relative(support), false);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block), hit);
            var state = block.getStateForPlacement(context);
            h.assertTrue(state != null && block.canSurvive(state, h.getLevel(), pos)
                    && state.getValue(PrimalCrystalClusterBlock.FACING) == support.getOpposite(), "Original rock support/pose rejected " + support);
            h.getLevel().setBlockAndUpdate(pos, state.setValue(PrimalCrystalClusterBlock.FACING, Direction.UP));
            block.neighborChanged(h.getLevel().getBlockState(pos), h.getLevel(), pos, Blocks.STONE, pos.relative(support), false);
            h.assertTrue(h.getLevel().getBlockState(pos).is(block), "Saved FACING removed another supported face " + support);
            h.getLevel().removeBlock(pos, false);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void woodenAndMetalSupportsAreRejectedButRockBrickAndOresWork(GameTestHelper h) {
        var pos = origin(h);
        for (Block block : new Block[]{Blocks.OAK_PLANKS, Blocks.IRON_BLOCK, Blocks.GLASS, Blocks.DIRT}) {
            h.getLevel().setBlockAndUpdate(pos.below(), block.defaultBlockState());
            h.assertTrue(!crystal("aer").canSurvive(crystal("aer").defaultBlockState(), h.getLevel(), pos), "Non-ROCK support accepted: " + block);
        }
        for (Block block : new Block[]{Blocks.BRICKS, Blocks.IRON_ORE, Blocks.OBSIDIAN, CatalogBlocks.block("stone_arcane")}) {
            h.getLevel().setBlockAndUpdate(pos.below(), block.defaultBlockState());
            h.assertTrue(crystal("aer").canSurvive(crystal("aer").defaultBlockState(), h.getLevel(), pos), "Original ROCK support rejected: " + block);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void finalSupportLossDropsSizePlusOneExactlyOnce(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 3, 2);
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        h.assertTrue(h.getLevel().isEmptyBlock(pos) && drops(h, pos, Aspect.AIR) == 4, "Support loss did not drop original four crystals exactly once");
        crystal("aer").neighborChanged(crystal("aer").defaultBlockState(), h.getLevel(), pos, Blocks.STONE, pos.below(), false);
        h.assertTrue(drops(h, pos, Aspect.AIR) == 4, "Replayed neighbor notification duplicated crystal drops"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void nativeBatterySupportsPaidClusterPlacementAndItsChargeChanges(GameTestHelper h) {
        var pos = origin(h); var battery = CatalogBlocks.block("vis_battery");
        assertNativeSupportPlacement(h, pos, battery.defaultBlockState(), Direction.DOWN, true);
        h.getLevel().setBlockAndUpdate(pos.below(), battery.defaultBlockState().setValue(VisBatteryBlock.CHARGE, 10));
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) && drops(h, pos, Aspect.AIR) == 0,
                "Battery charge update broke a cluster on its original ROCK face");
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.below(), battery.defaultBlockState());
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) && drops(h, pos, Aspect.AIR) == 0,
                "Replacing stone with an original ROCK battery destroyed the supported cluster");
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void originalTcStoneSlabsAndStairsAcceptClustersOnTheirSolidFaces(GameTestHelper h) {
        var pos = origin(h);
        for (String id : new String[]{"slab_arcane_stone", "slab_arcane_brick", "slab_ancient", "slab_eldritch"}) {
            var slab = CatalogBlocks.block(id);
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Direction.DOWN, true);
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Direction.UP, true);
            assertNativeSupportPlacement(h, pos, CatalogBlocks.block(id.replace("slab_", "slab_double_")).defaultBlockState(), Direction.DOWN, true);
        }
        for (String id : new String[]{"stairs_arcane", "stairs_arcane_brick", "stairs_ancient"}) {
            var stair = CatalogBlocks.block(id);
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.TOP), Direction.DOWN, true);
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM), Direction.UP, true);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void modernVanillaStoneShapesKeepFaceSpecificNativePlacement(GameTestHelper h) {
        var pos = origin(h);
        for (Block slab : new Block[]{Blocks.STONE_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB}) {
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Direction.DOWN, true);
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Direction.UP, true);
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Direction.DOWN, true);
        }
        for (Block stair : new Block[]{Blocks.STONE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS}) {
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.TOP), Direction.DOWN, true);
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM), Direction.UP, true);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void stoneOpenFacesAndSolidWoodShapesRejectPlacementWithoutPaying(GameTestHelper h) {
        var pos = origin(h);
        for (Block slab : new Block[]{Blocks.STONE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB,
                CatalogBlocks.block("slab_arcane_stone"), CatalogBlocks.block("slab_eldritch")}) {
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Direction.DOWN, false);
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Direction.UP, false);
        }
        for (Block stair : new Block[]{Blocks.STONE_STAIRS, CatalogBlocks.block("stairs_arcane")}) {
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM), Direction.DOWN, false);
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.TOP), Direction.UP, false);
        }
        for (Block slab : new Block[]{Blocks.OAK_SLAB, CatalogBlocks.block("slab_greatwood"), CatalogBlocks.block("slab_silverwood")})
            assertNativeSupportPlacement(h, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Direction.DOWN, false);
        for (Block stair : new Block[]{Blocks.OAK_STAIRS, CatalogBlocks.block("stairs_greatwood"), CatalogBlocks.block("stairs_silverwood")})
            assertNativeSupportPlacement(h, pos, stair.defaultBlockState().setValue(StairBlock.HALF, Half.TOP), Direction.DOWN, false);
        h.succeed();
    }
    /** Exercise the actual survival BlockItem payment, not just the support predicate. */
    private static void assertNativeSupportPlacement(GameTestHelper h, BlockPos pos, BlockState supportState,
            Direction towardSupport, boolean expected) {
        h.getLevel().removeBlock(pos, false);
        for (Direction dir : Direction.values()) h.getLevel().setBlockAndUpdate(pos.relative(dir), Blocks.AIR.defaultBlockState());
        BlockPos support = pos.relative(towardSupport);
        h.getLevel().setBlockAndUpdate(support, supportState);
        var player = player(h, pos); var stack = new ItemStack(crystal("aer"), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Direction face = towardSupport.getOpposite();
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, support, false);
        boolean placed = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
        String fixture = supportState + "/" + face;
        h.assertTrue(placed == expected && stack.getCount() == (expected ? 1 : 2), "Native placement/payment mismatch on " + fixture);
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) == expected && drops(h, pos, Aspect.AIR) == 0,
                "Unsupported placement mutated world or produced loot on " + fixture);
        if (expected) h.assertTrue(PrimalCrystalClusterBlock.supportMask(h.getLevel(), pos) == 1 << towardSupport.ordinal(),
                "Placement relied on an unrelated fixture support on " + fixture);
    }
    @GameTest(template = TEMPLATE) public static void anotherRockFacePreventsDropsWhenTheOldFacingSupportDisappears(GameTestHelper h) {
        var pos = origin(h); h.getLevel().setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
        plant(h, pos, "aer", 2, 2); h.getLevel().setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        h.assertTrue(h.getLevel().getBlockState(pos).is(crystal("aer")) && drops(h, pos, Aspect.AIR) == 0
                && PrimalCrystalClusterBlock.supportMask(h.getLevel(), pos) == 1 << Direction.EAST.ordinal(),
                "Amethyst single-FACING survival removed a cluster supported by another rock face"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void allSevenSizeBasedDropsIgnoreFortuneAndSilkAndKeepOriginalAspectNbt(GameTestHelper h) {
        var pos = origin(h);
        var silk = new ItemStack(Items.DIAMOND_PICKAXE); silk.enchant(Enchantments.SILK_TOUCH, 1);
        var fortune = new ItemStack(Items.DIAMOND_PICKAXE); fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        for (String type : TYPES) for (int size = 0; size < 4; size++) {
            var state = plant(h, pos, type, size, 4);
            for (ItemStack tool : new ItemStack[]{ItemStack.EMPTY, silk, fortune}) {
                var drops = Block.getDrops(state, h.getLevel(), pos, null, null, tool);
                h.assertTrue(drops.size() == 1 && drops.get(0).getCount() == size + 1
                        && ItemStack.isSameItemSameTags(drops.get(0), AspectCrystalItem.create(Aspect.getAspect(type))),
                        "Original drop count/contained aspect changed with enchantment: " + type + "/" + size);
            }
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void nativeSurvivalBreakPaysNoAuraAndHonorsForgeVeto(GameTestHelper h) {
        var pos = origin(h); plant(h, pos, "aer", 2, 3); aura(h, pos, 1000, 77);
        var player = player(h, pos); var callbacks = new AtomicInteger();
        Consumer<BlockEvent.BreakEvent> guard = event -> {
            if (event.getPlayer() == player && event.getPos().equals(pos)) { callbacks.incrementAndGet(); event.setCanceled(true); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        try { h.assertTrue(!player.gameMode.destroyBlock(pos), "Native canceled survival break succeeded"); }
        finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.assertTrue(callbacks.get() == 1 && size(h, pos) == 2 && drops(h, pos, Aspect.AIR) == 0, "Forge veto mutated state or emitted loot");
        h.assertTrue(player.gameMode.destroyBlock(pos) && h.getLevel().isEmptyBlock(pos) && drops(h, pos, Aspect.AIR) == 3
                && AuraManager.getVis(h.getLevel(), pos) == 1000 && AuraManager.getFlux(h.getLevel(), pos) == 77,
                "Uncanceled hand survival harvest failed original loot or altered aura"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void blockStateReloadPreservesSizeGenerationAndEarlierFacingProperties(GameTestHelper h) {
        var pos = origin(h);
        for (String type : TYPES) for (Direction facing : Direction.values()) {
            var state = plant(h, pos, type, 3, 4).setValue(PrimalCrystalClusterBlock.FACING, facing);
            var saved = NbtUtils.writeBlockState(state);
            var loaded = NbtUtils.readBlockState(h.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), saved);
            h.assertTrue(loaded.equals(state), "Cluster saved state lost original growth or earlier pose: " + type + "/" + facing);
        }
        h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void wetPlacementIsRejectedWhileEarlierPortWetStateStillLoads(GameTestHelper h) {
        var pos = origin(h); h.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        var player = player(h, pos); var block = crystal("aer");
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
        h.assertTrue(block.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block), hit)) == null,
                "Original no-liquid-placement rule inherited amethyst waterlogging");
        var old = block.defaultBlockState().setValue(PrimalCrystalClusterBlock.WATERLOGGED, true).setValue(PrimalCrystalClusterBlock.FACING, Direction.EAST);
        var saved = NbtUtils.writeBlockState(old);
        var loaded = NbtUtils.readBlockState(h.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), saved);
        h.assertTrue(loaded.equals(old), "Earlier port wet saved pose was discarded"); h.succeed();
    }
    @GameTest(template = TEMPLATE) public static void staleOrUnloadedRandomTicksCannotChangeAuraOrForceChunks(GameTestHelper h) {
        var pos = origin(h); var stale = plant(h, pos, "aer", 0, 1);
        plant(h, pos, "aer", 1, 1); aura(h, pos, 1000, 0);
        crystal("aer").randomTick(stale, h.getLevel(), pos, roll(1, true, false));
        h.assertTrue(size(h, pos) == 1 && AuraManager.getVis(h.getLevel(), pos) == 1000, "Stale random-tick callback paid twice");
        var unloaded = new BlockPos(10000000, 70, 10000000); var chunk = new ChunkPos(unloaded);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "Unloaded fixture unexpectedly loaded");
        crystal("aer").randomTick(stale, h.getLevel(), unloaded, roll(1, true, false));
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "Crystal random tick force-loaded a foreign chunk"); h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h, BlockPos pos) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "CrystalRuntime"));
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return player;
    }
}
