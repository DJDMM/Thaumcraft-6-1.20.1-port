package thaumcraft.world.crystal;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.aura.AuraChunk;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.aura.AuraSavedData;

/** Exact block-state storage and real local aura; the last scenario uses the ordinary scheduler. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class VisBatteryGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private VisBatteryGameTests() {}

    private static VisBatteryBlock battery() { return (VisBatteryBlock) CatalogBlocks.block("vis_battery"); }
    private static BlockPos place(GameTestHelper h, int charge) {
        BlockPos pos = h.absolutePos(CENTER);
        h.getLevel().setBlock(pos.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        h.getLevel().setBlock(pos, battery().defaultBlockState().setValue(VisBatteryBlock.CHARGE, charge), Block.UPDATE_ALL);
        return pos;
    }
    private static int charge(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockState(pos).getValue(VisBatteryBlock.CHARGE);
    }
    private static void update(GameTestHelper h, BlockPos pos, boolean random, long seed) {
        BlockState state = h.getLevel().getBlockState(pos);
        if (random) battery().randomTick(state, h.getLevel(), pos, RandomSource.create(seed));
        else battery().tick(state, h.getLevel(), pos, RandomSource.create(seed));
    }
    private static long scheduled(GameTestHelper h, BlockPos pos) {
        return ((LevelChunkTicks<Block>) h.getLevel().getChunkAt(pos).getBlockTicks()).getAll()
                .filter(tick -> tick.type() == battery() && tick.pos().equals(pos))
                .mapToLong(tick -> tick.triggerTick() - h.getLevel().getGameTime()).findFirst().orElse(-1);
    }
    private static void exact(GameTestHelper h, float actual, float expected, String why) {
        h.assertTrue(Float.compare(actual, expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static FakePlayer player(GameTestHelper h, BlockPos pos) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "battery_qa"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
        return player;
    }

    /** Replace only this loaded chunk's fixture aura; never expose a production setter or alter its base API. */
    private static final class AuraFixture implements AutoCloseable {
        private final Map<Long, AuraChunk> chunks;
        private final long key;
        private final AuraChunk previous;
        private final AuraSavedData data;
        @SuppressWarnings("unchecked") AuraFixture(GameTestHelper h, BlockPos pos, int base, float vis) {
            data = AuraSavedData.get(h.getLevel()); key = new ChunkPos(pos).toLong();
            try {
                Field field = AuraSavedData.class.getDeclaredField("chunks"); field.setAccessible(true);
                chunks = (Map<Long, AuraChunk>) field.get(data);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            previous = chunks.put(key, new AuraChunk(base, vis, 1000)); data.setDirty();
        }
        @Override public void close() {
            if (previous == null) chunks.remove(key); else chunks.put(key, previous);
            data.setDirty();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void tenUnitStateLightComparatorCubeAndOriginalPhysicalStats(GameTestHelper h) {
        var block = battery(); var pos = place(h, 0);
        h.assertTrue(block.defaultBlockState().getValue(VisBatteryBlock.CHARGE) == 0
                && block.getStateDefinition().getPossibleStates().size() == 11
                && block.getExplosionResistance() == .5F, "Default,11 states or original effective .5 resistance changed");
        for (int charge = 0; charge <= 10; charge++) {
            var state = block.defaultBlockState().setValue(VisBatteryBlock.CHARGE, charge);
            h.assertTrue(state.isRandomlyTicking() && state.getDestroySpeed(h.getLevel(), pos) == .5F
                    && !state.requiresCorrectToolForDrops() && state.hasAnalogOutputSignal()
                    && state.getAnalogOutputSignal(h.getLevel(), pos) == charge
                    && state.getLightEmission(h.getLevel(), pos) == charge && !state.hasBlockEntity()
                    && state.isCollisionShapeFullBlock(h.getLevel(), pos), "Original physical/state API changed at charge " + charge);
            for (Direction face : Direction.values()) h.assertTrue(state.isFaceSturdy(h.getLevel(), pos, face), "Full-cube support face became non-sturdy");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nativeStatePersistenceRetainsAllChargeValuesWithoutTileNbt(GameTestHelper h) {
        for (int charge = 0; charge <= 10; charge++) {
            var state = battery().defaultBlockState().setValue(VisBatteryBlock.CHARGE, charge);
            var saved = NbtUtils.writeBlockState(state);
            var restored = NbtUtils.readBlockState(h.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), saved);
            h.assertTrue(restored.equals(state) && restored.getValue(VisBatteryBlock.CHARGE) == charge,
                    "Native chunk block-state NBT lost charge " + charge);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void randomHighAuraDebitsExactlyOneAndSchedulesOneHundredTo199(GameTestHelper h) {
        BlockPos pos = place(h, 0); long seed = 113;
        try (var fixture = new AuraFixture(h, pos, 100, 95.5F)) {
            update(h, pos, true, seed);
            h.assertTrue(charge(h, pos) == 1 && scheduled(h, pos) == 100 + RandomSource.create(seed).nextInt(100),
                    "Native random tick did not retain charge1/exact100+nextInt100 continuation");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 94.5F, "Fractional local aura debit changed");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void exactNinetyPercentIsStableButSmallestHigherFloatCharges(GameTestHelper h) {
        BlockPos pos = place(h, 0);
        try (var fixture = new AuraFixture(h, pos, 100, 90)) {
            update(h, pos, false, 1);
            h.assertTrue(charge(h, pos) == 0 && scheduled(h, pos) == -1, "Equal90% charged or scheduled without a transfer");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 90, "Equal90% consumed aura");
        }
        try (var fixture = new AuraFixture(h, pos, 100, Math.nextUp(90F))) {
            update(h, pos, false, 1); h.assertTrue(charge(h, pos) == 1, "Strictly above90% failed to charge");
            exact(h, AuraManager.getVis(h.getLevel(), pos), Math.nextUp(90F) - 1, "Above90% debit was not exactly one");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oneVisIsNotChargeableEvenWithZeroAuraBase(GameTestHelper h) {
        BlockPos pos = place(h, 0);
        try (var fixture = new AuraFixture(h, pos, 0, 1)) {
            update(h, pos, false, 1); h.assertTrue(charge(h, pos) == 0 && scheduled(h, pos) == -1, "vis>1 became vis>=1");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 1, "Last single vis was consumed");
        }
        try (var fixture = new AuraFixture(h, pos, 0, 1.25F)) {
            update(h, pos, false, 1); h.assertTrue(charge(h, pos) == 1, "Fraction above one did not pay a full unit");
            exact(h, AuraManager.getVis(h.getLevel(), pos), .25F, "Charging rounded away the fractional remainder");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void capacityTenCannotDrawOrInventScheduledContinuation(GameTestHelper h) {
        BlockPos pos = place(h, 10);
        try (var fixture = new AuraFixture(h, pos, 100, 150)) {
            update(h, pos, true, 1);
            h.assertTrue(charge(h, pos) == 10 && scheduled(h, pos) == -1, "Full storage overcharged or scheduled");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 150, "Full battery consumed local aura");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lowAuraReturnsExactlyOneAndSchedulesTwentyTo39(GameTestHelper h) {
        BlockPos pos = place(h, 5); long seed = 113;
        try (var fixture = new AuraFixture(h, pos, 100, 20.25F)) {
            update(h, pos, false, seed);
            h.assertTrue(charge(h, pos) == 4 && scheduled(h, pos) == 20 + RandomSource.create(seed).nextInt(20),
                    "Low-aura scheduled tick changed its exact20+nextInt20 delay");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 21.25F, "Low aura did not receive exactly one unit");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void exactSeventyFivePercentAndDeadbandNeverTransfer(GameTestHelper h) {
        BlockPos pos = place(h, 5);
        for (float amount : new float[]{75, 80, 90}) try (var fixture = new AuraFixture(h, pos, 100, amount)) {
            update(h, pos, true, 1);
            h.assertTrue(charge(h, pos) == 5 && scheduled(h, pos) == -1, "Closed75..90% deadband transferred at " + amount);
            exact(h, AuraManager.getVis(h.getLevel(), pos), amount, "Deadband amount changed");
        }
        try (var fixture = new AuraFixture(h, pos, 100, Math.nextDown(75F))) {
            update(h, pos, false, 1); h.assertTrue(charge(h, pos) == 4, "Strictly below75% failed to release");
            exact(h, AuraManager.getVis(h.getLevel(), pos), Math.nextDown(75F) + 1, "Below75% release was not exactly one");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void poweredBatteryReleasesIntoOverfullAuraWithFiveTickContinuation(GameTestHelper h) {
        BlockPos pos = place(h, 2);
        // No neighbor notification here: inspect the continuation itself, not a prior one-tick signal trigger.
        h.getLevel().setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        try (var fixture = new AuraFixture(h, pos, 100, 200)) {
            update(h, pos, false, 1);
            h.assertTrue(charge(h, pos) == 1 && scheduled(h, pos) == 5, "Redstone did not release one unit/schedule5");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 201, "Redstone incorrectly capped release at aura base");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptyPoweredBatteryDoesNotCreateVisOrSchedule(GameTestHelper h) {
        BlockPos pos = place(h, 0); h.getLevel().setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        try (var fixture = new AuraFixture(h, pos, 100, 150)) {
            update(h, pos, true, 1);
            h.assertTrue(charge(h, pos) == 0 && scheduled(h, pos) == -1, "Empty powered storage charged or scheduled");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 150, "Empty powered storage created aura");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void originalAuraMaximumCanDiscardPoweredReleaseWhileChargeStillDecreases(GameTestHelper h) {
        BlockPos pos = place(h, 1); h.getLevel().setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        try (var fixture = new AuraFixture(h, pos, 100, 32766)) {
            update(h, pos, false, 1);
            h.assertTrue(charge(h, pos) == 0, "Battery refused original powered release at the aura storage maximum");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 32766, "Original AuraChunk32766 clamp changed");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tenStoredUnitsConserveLocalVisAcrossFullRelease(GameTestHelper h) {
        BlockPos pos = place(h, 10);
        try (var fixture = new AuraFixture(h, pos, 100, 0)) {
            for (int index = 0; index < 20; index++) update(h, pos, true, index);
            h.assertTrue(charge(h, pos) == 0, "Ten-unit battery failed to empty or went below zero");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 10, "Stored charge was lost or duplicated during release");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void staleStateReplacementAndUnloadedPositionCannotDebitAura(GameTestHelper h) {
        BlockPos pos = place(h, 3); var stale = h.getLevel().getBlockState(pos);
        h.getLevel().setBlock(pos, stale.setValue(VisBatteryBlock.CHARGE, 6), Block.UPDATE_ALL);
        try (var fixture = new AuraFixture(h, pos, 100, 99)) {
            battery().tick(stale, h.getLevel(), pos, RandomSource.create(1));
            h.assertTrue(charge(h, pos) == 6, "Stale scheduled state overwrote actual stored charge");
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            battery().randomTick(stale, h.getLevel(), pos, RandomSource.create(1));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE), "Removed battery restored itself");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 99, "Stale/replaced device debited aura");
        }
        BlockPos remote = new BlockPos(1_400_000, 100, 1_400_000); ChunkPos chunk = new ChunkPos(remote);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "Remote fixture is already loaded");
        var before = AuraSavedData.get(h.getLevel()).getChunk(chunk);
        battery().tick(stale, h.getLevel(), remote, RandomSource.create(1));
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                && AuraSavedData.get(h.getLevel()).getChunk(chunk) == before, "Battery loaded or initialized remote aura");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void actualSurvivalHandHarvestDropsOnePlainUnchargedItemAndNoAura(GameTestHelper h) {
        BlockPos pos = place(h, 10); var player = player(h, pos);
        try (var fixture = new AuraFixture(h, pos, 100, 80)) {
            h.assertTrue(player.hasCorrectToolForDrops(h.getLevel().getBlockState(pos)) && player.gameMode.destroyBlock(pos),
                    "Original hand-harvestable battery incorrectly required a tool");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1),
                    entity -> entity.getItem().is(battery().asItem()));
            h.assertTrue(h.getLevel().isEmptyBlock(pos) && drops.size() == 1 && drops.get(0).getItem().getCount() == 1
                    && !drops.get(0).getItem().hasTag(), "Charged battery lost/duplicated its plain metadata0 self drop");
            exact(h, AuraManager.getVis(h.getLevel(), pos), 80, "Destruction returned stored vis despite original damageDropped0");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ordinaryBlockItemPlacementUsesDefaultUnchargedNativeState(GameTestHelper h) {
        BlockPos support = h.absolutePos(CENTER.below()); h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = player(h, support); var stack = new ItemStack(battery(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false);
        h.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                && stack.getCount() == 1 && h.getLevel().getBlockState(support.above()).is(battery())
                && charge(h, support.above()) == 0 && h.getLevel().getBlockEntity(support.above()) == null,
                "Native BlockItem did not pay/place a fresh zero-charge block without a tile");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "vis_battery_native_tick", timeoutTicks = 45)
    public static void physicalRedstoneSignalStartsOrdinaryScheduledFullDischarge(GameTestHelper h) {
        BlockPos pos = place(h, 3); var fixture = new AuraFixture(h, pos, 100, 120);
        // Saturated flux prevents ordinary aura regeneration/diffusion; the device ignores flux just as BETA26 did.
        h.getLevel().setBlock(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(scheduled(h, pos) == 1, "Physical powered neighbor did not schedule original first update at1");
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(charge(h, pos) == 0, "Ordinary server scheduler did not release all3 stored units");
                exact(h, AuraManager.getVis(h.getLevel(), pos), 123, "Ordinary powered release lost or duplicated local aura");
                h.assertTrue(scheduled(h, pos) == -1, "Depleted battery kept scheduling its5-tick loop");
            } finally { fixture.close(); }
            h.succeed();
        });
    }
}
