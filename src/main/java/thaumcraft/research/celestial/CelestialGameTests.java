package thaumcraft.research.celestial;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchProgression;
import thaumcraft.research.theory.TheoryModule;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.scanning.ScanningModule;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CelestialGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "celestial_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, helper.getLevel().getMaxBuildHeight() - 5, pos.getZ() + 0.5);
        return player;
    }

    /** Use canonical progression rather than exposing a production setter for tests. */
    private static void research(GameTestHelper helper, ServerPlayer player, boolean complete) {
        ResearchProgression.advance(player, "KNOWLEDGETYPES", 0);
        ResearchProgression.advance(player, "THEORYRESEARCH", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(TheoryModule.SCRIBING_TOOLS.get()));
        KnowledgeStore.recordCraft(player, new ItemStack(TheoryModule.TABLE_ITEM.get()));
        ResearchProgression.advance(player, "THEORYRESEARCH", 1);
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 0) == ResearchProgression.Result.STARTED,
                "Canonical celestial fixture did not open");
        if (complete) {
            for (String category : List.of("BASICS", "ARTIFICE", "AUROMANCY"))
                KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, category, 16);
            helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 1) == ResearchProgression.Result.COMPLETE,
                    "Canonical celestial fixture did not complete");
        }
    }

    private static ItemStack tools() {
        ItemStack tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        tools.setDamageValue(100);
        tools.setHoverName(Component.literal("Exhausted but valid sky tools"));
        tools.getOrCreateTag().putString("CelestialTest", "preserve");
        return tools;
    }

    private static void supplies(ServerPlayer player, int papers) {
        player.getInventory().setItem(34, tools());
        player.getInventory().setItem(35, new ItemStack(Items.PAPER, papers));
    }

    private static void sun(ServerLevel level, ServerPlayer player) {
        level.setDayTime(6000);
        player.setYRot(-90); player.setXRot(-90);
    }

    private static int noteCount(ServerPlayer player, int metadata) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (CelestialModule.metadata(stack) == metadata) count += stack.getCount();
        }
        return count;
    }

    private static boolean thrownBy(ItemEntity item, ServerPlayer player) {
        CompoundTag saved = new CompoundTag();
        item.addAdditionalSaveData(saved);
        return saved.hasUUID("Thrower") && saved.getUUID("Thrower").equals(player.getUUID());
    }

    private static void result(GameTestHelper helper, CelestialScanner.Result actual, CelestialScanner.Result expected) {
        helper.assertTrue(actual == expected, "Expected celestial result " + expected + ", got " + actual);
    }

    /** Every test restores global clocks synchronously before another test can scan. */
    private record Clock(ServerLevel level, long gameTime, long dayTime, boolean rain, boolean thunder,
                         float rainLevel, float thunderLevel) implements AutoCloseable {
        static Clock save(ServerLevel level) {
            return new Clock(level, level.getGameTime(), level.getDayTime(), level.getLevelData().isRaining(),
                    level.getLevelData().isThundering(), level.getRainLevel(0), level.getThunderLevel(0));
        }
        void game(long value) { ((ServerLevelData) level.getLevelData()).setGameTime(value); }
        @Override public void close() {
            game(gameTime); level.setDayTime(dayTime);
            level.getLevelData().setRaining(rain);
            ((ServerLevelData) level.getLevelData()).setThundering(thunder);
            level.setRainLevel(rainLevel); level.setThunderLevel(thunderLevel);
        }
    }

    @GameTest(template = "empty")
    public static void allThirteenNotesRetainOriginalMetadataIdsAndAspects(GameTestHelper helper) {
        String[] suffixes = {"sun", "stars_1", "stars_2", "stars_3", "stars_4", "moon_1", "moon_2", "moon_3",
                "moon_4", "moon_5", "moon_6", "moon_7", "moon_8"};
        for (int md = 0; md < suffixes.length; md++) {
            ItemStack note = CelestialModule.note(md);
            helper.assertTrue(CelestialModule.metadata(note) == md && note.getMaxStackSize() == 64
                    && ForgeRegistries.ITEMS.getKey(note.getItem()).toString().equals("thaumcraft:celestial_notes_" + suffixes[md]),
                    "Original note metadata/ID/stack size changed at " + md);
            var aspects = AspectRegistry.getAspects(note);
            helper.assertTrue(aspects.size() == 3 && aspects.getAmount(Aspect.MIND) == 5
                    && aspects.getAmount(Aspect.DARKNESS) == 5 && aspects.getAmount(Aspect.LIGHT) == 5,
                    "Original wildcard celestial aspect template changed at " + md);
        }
        helper.assertTrue(CelestialModule.note(-1).isEmpty() && CelestialModule.note(13).isEmpty()
                && CelestialModule.metadata(ItemStack.EMPTY) == -1 && CelestialModule.metadata(new ItemStack(Items.PAPER)) == -1,
                "Invalid items or metadata became celestial notes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void solarAnglesKeepBinaryTruncationSignedRemainderAndStrictEdges(GameTestHelper helper) {
        float angle45 = 0.875F, angle135 = 0.125F;
        helper.assertTrue(CelestialScanner.variantForAngles(-80.01F, -51.99F, angle45, 0) == 0
                && CelestialScanner.variantForAngles(-99.99F, -39.01F, angle45, 0) == 0,
                "Fractional yaw/pitch were compared before BETA26 integer truncation");
        helper.assertTrue(CelestialScanner.variantForAngles(-80, -45, angle45, 0) == -1
                && CelestialScanner.variantForAngles(-100, -45, angle45, 0) == -1
                && CelestialScanner.variantForAngles(-90, -52, angle45, 0) == -1
                && CelestialScanner.variantForAngles(-90, -38, angle45, 0) == -1,
                "Strict ten/seven degree boundary became inclusive");
        helper.assertTrue(CelestialScanner.variantForAngles(99.99F, -45, angle135, 0) == 0
                && CelestialScanner.variantForAngles(-270, -45, angle135, 0) == 0
                && CelestialScanner.variantForAngles(100, -45, angle135, 0) == -1,
                "Western solar folding changed");
        helper.assertTrue(CelestialScanner.variantForAngles(-450, -45, angle45, 0) == 0
                && CelestialScanner.variantForAngles(-449, -45, angle45, 0) == -1
                && CelestialScanner.variantForAngles(-89, -45, angle45, 0) == 0,
                "Negative yaw was normalized instead of retaining signed remainder");
        helper.assertTrue(CelestialScanner.variantForAngles(0, 0, 0.25F, 0) == -1
                && CelestialScanner.variantForAngles(0, 0, 0.254F, 0) == 2
                && CelestialScanner.variantForAngles(-90, 0.01F, angle45, 0) == -1
                && CelestialScanner.variantForAngles(Float.NaN, -45, angle45, 0) == -1,
                "Night threshold, downward gaze, or invalid angle check changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void starsUseOriginalHorizontalFacingAndAllMoonPhaseMetadata(GameTestHelper helper) {
        float[] yaws = {180, 0, 90, -90, -180, 270, -270};
        int[] metadata = {1, 2, 3, 4, 1, 4, 3};
        for (int i = 0; i < yaws.length; i++)
            helper.assertTrue(CelestialScanner.variantForAngles(yaws[i], 0, 0.5F, 0) == metadata[i],
                    "Adjusted facing assigned wrong star note at yaw " + yaws[i]);
        helper.assertTrue(CelestialScanner.variantForAngles(Math.nextDown(45.0F), 0, 0.5F, 0) == 2
                && CelestialScanner.variantForAngles(45, 0, 0.5F, 0) == 3
                && CelestialScanner.variantForAngles(-45, 0, 0.5F, 0) == 2
                && CelestialScanner.variantForAngles(Math.nextDown(-45.0F), 0, 0.5F, 0) == 4,
                "Original half-quadrant facing boundary changed");
        for (int phase = 0; phase < 8; phase++)
            helper.assertTrue(CelestialScanner.variantForAngles(-90, -90, 0.5F, phase) == 5 + phase,
                    "Moon phase no longer uses original metadata offset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completedResearchOpenSkyAndUpwardLookAreRequiredButRainIsAllowed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        supplies(player, 4);
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.NOT_APPLICABLE);
            research(helper, player, false);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.NOT_APPLICABLE);
            for (String category : List.of("BASICS", "ARTIFICE", "AUROMANCY"))
                KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, category, 16);
            ResearchProgression.advance(player, "CELESTIALSCANNING", 1);
        }
        BlockPos roof = player.blockPosition().above(2);
        var previous = helper.getLevel().getBlockState(roof);
        helper.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
        // In 1.20.1 canSeeSky reads asynchronously propagated skylight. Let the real light engine update first.
        helper.runAfterDelay(5, () -> {
            try (Clock clock = Clock.save(helper.getLevel())) {
                sun(helper.getLevel(), player);
                helper.assertTrue(!helper.getLevel().canSeeSky(player.blockPosition().above()), "Roof fixture skylight has not propagated");
                result(helper, CelestialScanner.scan(player), CelestialScanner.Result.NOT_APPLICABLE);
                // A separate open column avoids relying on roof-removal light propagation for the next assertion.
                player.setPos(player.getX() + 2, player.getY(), player.getZ());
                helper.assertTrue(helper.getLevel().canSeeSky(player.blockPosition().above()), "Rain fixture is not exposed to sky");
                player.setXRot(1);
                result(helper, CelestialScanner.scan(player), CelestialScanner.Result.NOT_APPLICABLE);
                helper.assertTrue(player.getInventory().getItem(35).getCount() == 4, "Ineligible sky scan spent paper");
                player.setXRot(-90);
                helper.getLevel().getLevelData().setRaining(true);
                ((ServerLevelData) helper.getLevel().getLevelData()).setThundering(true);
                helper.getLevel().setRainLevel(1); helper.getLevel().setThunderLevel(1);
                result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
                helper.assertTrue(noteCount(player, 0) == 1 && player.getInventory().getItem(35).getCount() == 3,
                        "BETA26 rain scan did not produce/pay one solar note");
            } finally { helper.getLevel().setBlockAndUpdate(roof, previous); }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void onlyMainInventorySuppliesCountAndExhaustedToolsRemainUnspent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true);
        ItemStack originalTools = tools();
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            player.getInventory().setItem(35, new ItemStack(Items.PAPER, 3));
            player.getInventory().setItem(40, originalTools.copy());
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.MISSING_RESOURCES);
            player.getInventory().setItem(40, ItemStack.EMPTY);
            player.getInventory().setItem(34, originalTools.copy());
            player.getInventory().setItem(35, ItemStack.EMPTY);
            player.getInventory().setItem(40, new ItemStack(Items.PAPER, 3));
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.MISSING_RESOURCES);
            player.getInventory().setItem(40, ItemStack.EMPTY);
            ItemStack paper = new ItemStack(Items.PAPER, 3); paper.setHoverName(Component.literal("Named paper"));
            player.getInventory().setItem(35, paper);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(34), originalTools)
                    && player.getInventory().getItem(35).getCount() == 2 && noteCount(player, 0) == 1,
                    "Full main-slot search, relaxed paper NBT, or wildcard exhausted tool handling changed");
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.ALREADY_RECORDED);
            helper.assertTrue(player.getInventory().getItem(35).getCount() == 2 && noteCount(player, 0) == 1,
                    "Replay debited paper or created another note");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allVariantsHaveIndependentGameTimeQuotaWhileMoonUsesDayTime(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true); supplies(player, 20);
        try (Clock clock = Clock.save(helper.getLevel())) {
            clock.game(3 * 24000L + 100);
            sun(helper.getLevel(), player);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            helper.getLevel().setDayTime(6000 + 40 * 24000L);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.ALREADY_RECORDED);
            float[] starYaw = {180, 0, 90, -90};
            helper.getLevel().setDayTime(18000);
            player.setXRot(0);
            for (float yaw : starYaw) { player.setYRot(yaw); result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED); }
            player.setYRot(-90); player.setXRot(-90);
            for (int phase = 0; phase < 8; phase++) {
                helper.getLevel().setDayTime(phase * 24000L + 18000);
                result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            }
            PlayerKnowledge restored = PlayerKnowledge.load(KnowledgeStore.get(player).save());
            for (int md = 0; md < 13; md++)
                helper.assertTrue(noteCount(player, md) == 1 && restored.hasCelestial(3, md), "Variant/quota was lost at " + md);
            helper.assertTrue(player.getInventory().getItem(35).getCount() == 7
                    && restored.researchKeys().stream().noneMatch(key -> key.startsWith("CEL_")),
                    "Thirteen distinct notes did not pay exactly thirteen paper or quota leaked into research");
            clock.game(4 * 24000L);
            sun(helper.getLevel(), player);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            helper.assertTrue(noteCount(player, 0) == 2 && KnowledgeStore.get(player).hasCelestial(4, 0)
                    && !KnowledgeStore.get(player).hasCelestial(4, 1), "Game-time day did not renew the quota");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonOverworldDimensionTypesCannotRecordSky(GameTestHelper helper) {
        for (var key : List.of(Level.NETHER, Level.END)) {
            ServerLevel level = helper.getLevel().getServer().getLevel(key);
            helper.assertTrue(level != null, "Test server lacks vanilla dimension " + key.location());
            ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "celestial_dimension"));
            player.setPos(0.5, level.getMaxBuildHeight() - 5, 0.5);
            research(helper, player, true); supplies(player, 2);
            player.setYRot(-90); player.setXRot(-90);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.NOT_APPLICABLE);
            helper.assertTrue(player.getInventory().getItem(35).getCount() == 2 && noteCount(player, 0) == 0,
                    "Wrong dimension spent supplies or produced a note");
        }
        helper.succeed();
    }

    private static void fullInventory(ServerPlayer player, int paper) {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        supplies(player, paper);
    }

    @GameTest(template = "empty")
    public static void fullInventoryPreflightsCancelledDropWithoutPaperOrQuotaLoss(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true); fullInventory(player, 3);
        int[] observed = {0};
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof ItemEntity item && CelestialModule.metadata(item.getItem()) == 0
                    && thrownBy(item, player)) { observed[0]++; event.setCanceled(true); }
        };
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try { result(helper, CelestialScanner.scan(player), CelestialScanner.Result.DELIVERY_FAILED); }
            finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(observed[0] == 1 && player.getInventory().getItem(35).getCount() == 3
                    && !KnowledgeStore.get(player).hasCelestial(helper.getLevel().getGameTime() / 24000L, 0),
                    "Cancelled real entity-join hook charged paper or consumed quota");
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3),
                    item -> thrownBy(item, player) && CelestialModule.metadata(item.getItem()) == 0);
            try {
                helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 1
                        && noteCount(player, 0) == 0 && player.getInventory().getItem(35).getCount() == 2,
                        "Full inventory did not deliver exactly one paid note");
                result(helper, CelestialScanner.scan(player), CelestialScanner.Result.ALREADY_RECORDED);
                helper.assertTrue(dropped.get(0).getItem().getCount() == 1 && player.getInventory().getItem(35).getCount() == 2,
                        "Full inventory replay duplicated output or payment");
            } finally { dropped.forEach(ItemEntity::discard); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalPaperSlotAndMatchingOffhandStackAvoidDropping(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true); fullInventory(player, 1);
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            helper.assertTrue(CelestialModule.metadata(player.getInventory().getItem(35)) == 0,
                    "Final consumed paper slot was not used for output");
            clock.game((helper.getLevel().getGameTime() / 24000L + 1) * 24000L);
            fullInventory(player, 2);
            ItemStack note = CelestialModule.note(0); note.setCount(63);
            player.getInventory().setItem(40, note);
            result(helper, CelestialScanner.scan(player), CelestialScanner.Result.CREATED);
            helper.assertTrue(player.getInventory().getItem(40).getCount() == 64 && player.getInventory().getItem(35).getCount() == 1,
                    "Original matching offhand insertion priority changed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityJoinRevocationRollsBackDeliveryAndPayment(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true); fullInventory(player, 3);
        Consumer<EntityJoinLevelEvent> revoke = event -> {
            if (event.getEntity() instanceof ItemEntity item && thrownBy(item, player)) player.setHealth(0);
        };
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            MinecraftForge.EVENT_BUS.addListener(revoke);
            try { result(helper, CelestialScanner.scan(player), CelestialScanner.Result.LOCKED); }
            finally { MinecraftForge.EVENT_BUS.unregister(revoke); }
            helper.assertTrue(player.getInventory().getItem(35).getCount() == 3
                    && !KnowledgeStore.get(player).hasCelestial(helper.getLevel().getGameTime() / 24000L, 0)
                    && helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3),
                            item -> thrownBy(item, player)).isEmpty(),
                    "Entity-join eligibility revocation left output, quota or a paper debit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualThaumometerUseHonorsHandDailyQuotaAndObjectTargetBeforeSky(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        research(helper, player, true); supplies(player, 4);
        var thaumometer = ScanningModule.THAUMOMETER.get();
        try (Clock clock = Clock.save(helper.getLevel())) {
            sun(helper.getLevel(), player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(noteCount(player, 0) == 0 && player.getInventory().getItem(35).getCount() == 4, "Wrong hand item invoked sky scanning");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(thaumometer));
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(noteCount(player, 0) == 1 && player.getInventory().getItem(35).getCount() == 3, "Actual thaumometer use did not create a sky note");
            clock.game((helper.getLevel().getGameTime() / 24000L + 1) * 24000L);
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(noteCount(player, 0) == 2 && player.getInventory().getItem(35).getCount() == 2, "Extra item cooldown blocked the next day's independent sky scan");
            player.getCooldowns().removeCooldown(thaumometer);
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL));
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(KnowledgeStore.get(player).scanCount() == 1 && noteCount(player, 0) == 2
                    && player.getInventory().getItem(35).getCount() == 2, "A real inventory target also invoked the sky fallback");
            player.getCooldowns().removeCooldown(thaumometer);
            player.setShiftKeyDown(false);
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(noteCount(player, 0) == 2 && player.getInventory().getItem(35).getCount() == 2, "Same-day sky repeat bypassed the original daily quota");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void notesAreScannableThroughThaumometerWithOriginalAspectTemplate(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var thaumometer = ScanningModule.THAUMOMETER.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(thaumometer));
        player.setShiftKeyDown(true);
        for (int md = 0; md < 13; md++) {
            player.setItemInHand(InteractionHand.OFF_HAND, CelestialModule.note(md));
            player.getCooldowns().removeCooldown(thaumometer);
            thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(KnowledgeStore.get(player).scanCount() == md + 1, "Note variant was not scannable at " + md);
        }
        PlayerKnowledge knowledge = KnowledgeStore.get(player);
        helper.assertTrue(knowledge.knowsAspect(Aspect.MIND) && knowledge.knowsAspect(Aspect.DARKNESS) && knowledge.knowsAspect(Aspect.LIGHT),
                "Actual note scan did not reveal original aspect template");
        var before = knowledge.save();
        ItemStack renamed = CelestialModule.note(12); renamed.setCount(64); renamed.setHoverName(Component.literal("Same celestial variant"));
        player.setItemInHand(InteractionHand.OFF_HAND, renamed);
        player.getCooldowns().removeCooldown(thaumometer);
        thaumometer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(knowledge.save().equals(before), "Renaming/restacking a note farmed a duplicate observation");
        helper.succeed();
    }
}
