package thaumcraft.golemancy.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.blocks.*;
import thaumcraft.research.theory.TheoryAids;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class BrainJarGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private BrainJarGameTests() {}
    private static BrainJarBlockEntity jar(GameTestHelper helper) {
        helper.setBlock(CENTER, CatalogBlocks.block("jar_brain"));
        return (BrainJarBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }
    private static void tick(GameTestHelper helper, BrainJarBlockEntity jar, int amount) {
        for (int i = 0; i < amount; i++) BrainJarBlockEntity.tick(helper.getLevel(), jar.getBlockPos(), jar.getBlockState(), jar);
    }
    private static void xp(BrainJarBlockEntity jar, int amount) {
        var tag = new CompoundTag(); tag.putInt("XP", amount); jar.load(tag);
    }
    private static ExperienceOrb orb(GameTestHelper helper, BrainJarBlockEntity jar, Vec3 offset, int amount) {
        Vec3 pos = Vec3.atCenterOf(jar.getBlockPos()).add(offset);
        var orb = new ExperienceOrb(helper.getLevel(), pos.x, pos.y, pos.z, amount);
        orb.setDeltaMovement(Vec3.ZERO); orb.setNoGravity(true); helper.getLevel().addFreshEntity(orb); return orb;
    }
    private static void clean(GameTestHelper helper, BrainJarBlockEntity jar) {
        helper.getLevel().removeBlock(jar.getBlockPos(), false);
    }
    private static List<ExperienceOrb> released(GameTestHelper helper, BrainJarBlockEntity jar) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(jar.getBlockPos()).inflate(.2));
    }

    @GameTest(template = TEMPLATE)
    public static void physicalJarKeepsOriginalShapeResistanceComparatorAndEnchantPower(GameTestHelper helper) {
        var jar = jar(helper); var state = jar.getBlockState(); var block = (BrainJarBlock)state.getBlock();
        helper.assertTrue(jar.getType() == BrainJarModule.BRAIN_JAR.get() && block.asItem() instanceof BrainJarBlockItem,
                "Working jar kept visual catalogue tile/item");
        helper.assertTrue(state.getDestroySpeed(helper.getLevel(), jar.getBlockPos()) == .3F && block.getExplosionResistance() == 12
                && !state.requiresCorrectToolForDrops() && state.getShape(helper.getLevel(), jar.getBlockPos()).bounds().equals(new AABB(.1875, 0, .1875, .8125, .75, .8125)),
                "Original .3 hardness/effective12 resistance/12px shape changed");
        helper.assertTrue(block.getEnchantPowerBonus(state, helper.getLevel(), jar.getBlockPos()) == 5 && state.hasAnalogOutputSignal(),
                "Original five enchanting power or comparator vanished");
        int[] amounts = {0, 1, 999, 1000, 1999, 2000}; int[] signals = {0, 1, 7, 8, 14, 15};
        for (int i = 0; i < amounts.length; i++) { xp(jar, amounts[i]); helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), jar.getBlockPos()) == signals[i], "XP comparator changed at " + amounts[i]); }
        clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void originalNearestBoxAndAnisotropicMotionPullOnlyClosestOrb(GameTestHelper helper) {
        var jar = jar(helper);
        var near = orb(helper, jar, new Vec3(2, 1, 2), 3);
        var far = orb(helper, jar, new Vec3(4, 0, 0), 5);
        helper.assertTrue(jar.closestOrb() == near, "Nearest orb did not use centre-squared distance");
        tick(helper, jar, 1);
        Vec3 direction = new Vec3(-2, -1, -2).scale(1D / 25);
        double distance = direction.length(), strength = (1 - distance) * (1 - distance);
        Vec3 expected = new Vec3(direction.x / distance * strength * .3, direction.y / distance * strength * .5,
                direction.z / distance * strength * .3);
        helper.assertTrue(near.getDeltaMovement().distanceTo(expected) < 1E-10 && far.getDeltaMovement().equals(Vec3.ZERO)
                && jar.xp() == 0 && near.isAlive() && far.isAlive(), "Suction speed/axis multipliers or nearest-only rule changed");
        near.discard(); far.discard();
        var corner = orb(helper, jar, new Vec3(7, 7, 7), 3);
        helper.assertTrue(jar.closestOrb() == corner && corner.distanceToSqr(Vec3.atCenterOf(jar.getBlockPos())) > 64,
                "Original eight-expanded AABB became an eight-radius sphere");
        corner.discard(); var outside = orb(helper, jar, new Vec3(10, 0, 0), 3);
        helper.assertTrue(jar.closestOrb() != outside, "Out-of-box orb selected");
        outside.discard(); clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void consumingAllContactOrbsKeepsOriginalNextTickOverflowLoss(GameTestHelper helper) {
        var jar = jar(helper); xp(jar, 1999);
        var a = orb(helper, jar, new Vec3(.1, 0, 0), 7); var b = orb(helper, jar, new Vec3(-.1, 0, 0), 3);
        tick(helper, jar, 1);
        helper.assertTrue(jar.xp() == 2009 && a.isRemoved() && b.isRemoved(), "Contact pass stopped at capacity or preserved old overflow");
        helper.assertTrue(jar.saveWithoutMetadata().getInt("XP") == 2009 && jar.getUpdatePacket().getTag().getInt("XP") == 2009,
                "Committed contact payment was not saved/synchronized");
        tick(helper, jar, 1);
        helper.assertTrue(jar.xp() == 2000, "Next-tick BETA26 capacity clamp changed");
        var ignored = orb(helper, jar, new Vec3(.1, 0, 0), 3); tick(helper, jar, 1);
        helper.assertTrue(ignored.isAlive() && jar.xp() == 2000 && ignored.getDeltaMovement().equals(Vec3.ZERO), "Full jar still pulls or consumes");
        ignored.discard(); clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void modernMergedOrbCountPreservesAllUnderlyingXpAndCannotDoubleCredit(GameTestHelper helper) {
        var jar = jar(helper); var orb = orb(helper, jar, Vec3.ZERO, 7);
        var tag = new CompoundTag(); orb.addAdditionalSaveData(tag); tag.putInt("Count", 5); orb.readAdditionalSaveData(tag);
        helper.assertTrue(BrainJarBlockEntity.totalOrbXp(orb) == 35, "Merged Count fixture is not worth five old orbs");
        tick(helper, jar, 1);
        helper.assertTrue(jar.xp() == 35 && orb.isRemoved(), "Jar discarded merged Count while crediting only getValue");
        tick(helper, jar, 3);
        helper.assertTrue(jar.xp() == 35, "Removed orb was consumed twice");
        clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void originalRandomActivationPaysExactAmountAsRealSplitOrbsIncludingZero(GameTestHelper helper) {
        var jar = jar(helper); var player = helper.makeMockPlayer(); player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 2));
        boolean zero = false, nonzero = false;
        for (long seed = 0; seed < 100 && !(zero && nonzero); seed++) {
            xp(jar, 17); helper.getLevel().random.setSeed(seed); int expected = new java.util.Random(seed).nextInt(18);
            var result = jar.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(jar.getBlockPos()), Direction.UP, jar.getBlockPos(), false));
            var orbs = released(helper, jar); long emitted = orbs.stream().mapToLong(BrainJarBlockEntity::totalOrbXp).sum();
            helper.assertTrue(result.consumesAction() && jar.xp() == 17 - expected && emitted == expected && jar.eatDelay() == 40
                    && player.getMainHandItem().getCount() == 2, "Ungated activation altered random payment, returned item, delay or actual orb sum seed=" + seed);
            zero |= expected == 0; nonzero |= expected > 0; orbs.forEach(ExperienceOrb::discard);
        }
        helper.assertTrue(zero && nonzero, "Activation fixture did not exercise zero and positive rolls");
        xp(jar, 2000); helper.getLevel().random.setSeed(12); int expected = new java.util.Random(12).nextInt(64);
        helper.assertTrue(jar.releaseXp() == expected && jar.xp() == 2000 - expected
                && released(helper, jar).stream().mapToLong(BrainJarBlockEntity::totalOrbXp).sum() == expected && expected <= 63,
                "Large stock release did not cap roll at63 or debit exactly actual split XP");
        released(helper, jar).forEach(ExperienceOrb::discard); clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void activationPauseLastsFortyCallbacksThenCaptureResumes(GameTestHelper helper) {
        var jar = jar(helper); helper.assertTrue(jar.releaseXp() == 0 && jar.eatDelay() == 40, "Empty activation did not still pause capture");
        var near = orb(helper, jar, new Vec3(.1, 0, 0), 7);
        tick(helper, jar, 40);
        helper.assertTrue(jar.xp() == 0 && near.isAlive() && near.getDeltaMovement().equals(Vec3.ZERO) && jar.eatDelay() == 0,
                "Jar captured or pulled before forty complete callbacks");
        tick(helper, jar, 1);
        helper.assertTrue(near.isRemoved() && jar.xp() == 7, "First unpaused callback did not resume consumption");
        clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void savesUseUpperXpItemsUseLowerXpAndTransientDelayIsNotSaved(GameTestHelper helper) {
        var jar = jar(helper); xp(jar, 31); helper.getLevel().random.setSeed(4); jar.releaseXp();
        int amount = jar.xp(); var saved = jar.saveWithFullMetadata(); var item = jar.asItemStack();
        helper.assertTrue(saved.getInt("XP") == amount && saved.getString("id").equals("thaumcraft:brain_jar")
                && !saved.contains("eatDelay") && !saved.contains("rotation") && !saved.contains("xpMax")
                && item.getTag().getInt("xp") == amount && !item.getTag().contains("XP"), "Original upper/lower XP keys or transient fields changed");
        var loaded = BlockEntity.loadStatic(jar.getBlockPos(), jar.getBlockState(), saved);
        helper.assertTrue(loaded instanceof BrainJarBlockEntity copy && copy.xp() == amount && copy.eatDelay() == 0, "Reload lost XP or persisted forty-tick pause");
        helper.getLevel().removeBlock(jar.getBlockPos(), false); var placed = jar(helper);
        placed.getBlockState().getBlock().setPlacedBy(helper.getLevel(), placed.getBlockPos(), placed.getBlockState(), null, item);
        helper.assertTrue(placed.xp() == amount && placed.getUpdateTag().getInt("XP") == amount, "Placement did not restore lower-case item xp and synchronize");
        released(helper, placed).forEach(ExperienceOrb::discard); clean(helper, placed); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void actualDestructionDropsExactlyOnePortableXpJarAndNoLooseXp(GameTestHelper helper) {
        var jar = jar(helper); var orb = orb(helper, jar, new Vec3(.1, 0, 0), 17); tick(helper, jar, 1);
        helper.assertTrue(orb.isRemoved() && jar.xp() == 17, "Drop fixture did not capture real XP");
        helper.getLevel().destroyBlock(jar.getBlockPos(), true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(jar.getBlockPos()).inflate(1));
        long count = drops.stream().filter(e -> e.getItem().is(CatalogBlocks.block("jar_brain").asItem())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(count == 1 && drops.stream().filter(e -> e.getItem().is(CatalogBlocks.block("jar_brain").asItem()))
                .allMatch(e -> e.getItem().getTag().getInt("xp") == 17) && released(helper, jar).isEmpty(),
                "Destruction duplicated the jar or lost/released its original saved item XP");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void registeredServerTickerConsumesRealOrbAndRealDeviceRemainsTheoryAid(GameTestHelper helper) {
        var jar = jar(helper); var orb = orb(helper, jar, new Vec3(.1, 0, 0), 7);
        helper.assertTrue(TheoryAids.matches(TheoryAids.BRAIN_IN_A_JAR, jar.getBlockState())
                && TheoryAids.find(helper.getLevel(), jar.getBlockPos().east()).contains(TheoryAids.BRAIN_IN_A_JAR),
                "Operational replacement lost original physical theory aid identity");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(jar.xp() == 7 && orb.isRemoved(), "Registered real server ticker did not consume actual world XP");
            clean(helper, jar); helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void malformedNbtAndExactCentreMotionCannotInventNegativeXpOrNan(GameTestHelper helper) {
        var jar = jar(helper);
        for (var face : net.minecraft.core.Direction.values()) helper.assertTrue(!jar.getBlockState().isFaceSturdy(helper.getLevel(), jar.getBlockPos(), face, net.minecraft.world.level.block.SupportType.CENTER), "Original jar UNDEFINED face became a modern attachment support: " + face);
        xp(jar, -5);
        helper.assertTrue(jar.xp() == 0 && !jar.asItemStack().hasTag(), "Negative tile XP remained releasable");
        var bad = new ItemStack(CatalogBlocks.block("jar_brain")); bad.getOrCreateTag().putInt("xp", -5); jar.readItem(bad);
        helper.assertTrue(jar.xp() == 0, "Negative carried XP remained");
        var zero = orb(helper, jar, Vec3.ZERO, 0); tick(helper, jar, 1);
        helper.assertTrue(zero.isAlive() && jar.xp() == 0 && zero.getDeltaMovement().equals(Vec3.ZERO), "Zero-value exact-centre orb created NaN motion/XP");
        zero.discard(); xp(jar, Integer.MAX_VALUE); tick(helper, jar, 1);
        helper.assertTrue(jar.xp() == 2000, "Oversized NBT did not receive original next-tick clamp");
        clean(helper, jar); helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void boundedLegacyMigrationChangesOnlyStorageFreeAnchorAndKeepsWorkingXp(GameTestHelper helper) {
        var jar = jar(helper); var level = helper.getLevel();
        helper.setBlock(CENTER.west(), CatalogBlocks.block("jar_brain"));
        var active = (BrainJarBlockEntity)level.getBlockEntity(jar.getBlockPos().west()); xp(active, 71);
        var legacy = new CatalogBlockEntity(jar.getBlockPos(), jar.getBlockState());
        var chunk = level.getChunkAt(jar.getBlockPos()); chunk.addAndRegisterBlockEntity(legacy);
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try { LegacyBrainJarMigration.loadBrainJarChunk(new ChunkEvent.Load(chunk, false)); }
            catch (Throwable thrown) { failure.set(thrown); } }, "tc6-brain-jar-chunk-worker");
        worker.start(); try { worker.join(5000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        helper.assertTrue(!worker.isAlive() && failure.get() == null && level.getBlockEntity(jar.getBlockPos()) == legacy, "Worker migration directly mutated world");
        for (int i = 0; i < 200 && level.getBlockEntity(jar.getBlockPos()) == legacy; i++)
            LegacyBrainJarMigration.tickBrainJarMigration(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
        helper.assertTrue(level.getBlockEntity(jar.getBlockPos()) instanceof BrainJarBlockEntity fresh && fresh.xp() == 0 && legacy.isRemoved()
                && level.getBlockEntity(active.getBlockPos()) == active && active.xp() == 71 && chunk.isUnsaved(), "Migration invented XP or replaced working device contents");
        var saved = chunk.getBlockEntityNbtForSaving(jar.getBlockPos());
        helper.assertTrue(saved != null && saved.getString("id").equals("thaumcraft:brain_jar") && BlockEntity.loadStatic(jar.getBlockPos(), jar.getBlockState(), saved) instanceof BrainJarBlockEntity,
                "Migrated chunk saved old visual anchor ID");
        level.removeBlock(active.getBlockPos(), false); clean(helper, jar); helper.succeed();
    }
}
