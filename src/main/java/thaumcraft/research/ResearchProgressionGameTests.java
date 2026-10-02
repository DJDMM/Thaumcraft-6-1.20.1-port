package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.alchemy.CrucibleRecipes;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchProgressionGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "progress_test"));
    }
    private static void firstSteps(ServerPlayer player) {
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        ResearchProgression.advance(player, "FIRSTSTEPS", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        ResearchProgression.advance(player, "FIRSTSTEPS", 1);
        KnowledgeStore.recordCraft(player, new ItemStack(ScanningModule.THAUMOMETER.get()));
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 16);
        ResearchProgression.advance(player, "FIRSTSTEPS", 2);
    }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty")
    public static void firstStepsSeparatesCraftProofFromInventoryAndSpendsOnlyOnAdvance(GameTestHelper helper) {
        var player = player(helper);
        var state = KnowledgeStore.get(player);
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 0), ResearchProgression.Result.LOCKED);
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 19);
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(state.researchStage("FIRSTSTEPS") == 1 && state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 19, "Opening charged a stage");
        helper.assertTrue(state.isResearchCompleteStrict("KNOWLEDGETYPES") && state.knowsResearch("!gotdream"), "Initial siblings not revealed");
        player.getInventory().setItem(9, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.knowsResearch("FIRSTSTEPS@2") && !state.isResearchCompleteStrict("FIRSTSTEPS"), "Entering stage was confused with completion");
        player.getInventory().setItem(10, new ItemStack(ScanningModule.THAUMOMETER.get()));
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordCraft(player, new ItemStack(ScanningModule.THAUMOMETER.get()));
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 2), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("FIRSTSTEPS") == 4 && state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 3, "Final text skip or raw remainder wrong");
        helper.assertTrue(player.getInventory().getItem(9).getCount() == 1 && player.getInventory().getItem(10).getCount() == 1, "Craft proof consumed inventory");
        helper.assertTrue(player.totalExperience == 20, "Stage/sibling reward count wrong");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knowledgeCostsAreAtomicAndReplayedStagesCannotChargeAgain(GameTestHelper helper) {
        var player = player(helper); firstSteps(player);
        var state = KnowledgeStore.get(player);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 21);
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 0), ResearchProgression.Result.STARTED);
        int experience = player.totalExperience;
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.researchStage("UNLOCKALCHEMY") == 1 && state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 21 && player.totalExperience == experience, "Failed multi-category payment mutated state");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 18);
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 5 && state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 2, "Knowledge cost/remainder wrong");
        KnowledgeStore.recordCraft(player, new ItemStack(AlchemyModule.CRUCIBLE_ITEM.get()));
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 1), ResearchProgression.Result.STALE);
        helper.assertTrue(state.researchStage("UNLOCKALCHEMY") == 2 && player.totalExperience == experience + 5, "Replay paid the following stage");
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", -1), ResearchProgression.Result.STALE);
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 4), ResearchProgression.Result.STALE);
        result(helper, ResearchProgression.advance(player, "INFUSION", 0), ResearchProgression.Result.UNSUPPORTED);
        result(helper, ResearchProgression.advance(player, "does_not_exist", 0), ResearchProgression.Result.UNSUPPORTED);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realArcaneCraftRecordsEvidenceOnlyAfterPayment(GameTestHelper helper) {
        var player = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE));
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get()));
        AuraManager.drainVis(helper.getLevel(), pos, Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), pos, 100);
        helper.assertTrue(bench.craft(player).isEmpty() && !KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer") && bench.getItem(1).getCount() == 1, "Locked arcane craft wrote evidence/consumed resources");
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        ResearchProgression.advance(player, "FIRSTSTEPS", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        ResearchProgression.advance(player, "FIRSTSTEPS", 1);
        var output = bench.craft(player);
        helper.assertTrue(output.is(ScanningModule.THAUMOMETER.get()) && KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer") && AuraManager.getVis(helper.getLevel(), pos) == 80, "Committed arcane craft did not record evidence");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 16);
        result(helper, ResearchProgression.advance(player, "FIRSTSTEPS", 2), ResearchProgression.Result.COMPLETE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nitorRecipeOpensBeforeItsRequiredCraftAndAlchemyFinishes(GameTestHelper helper) {
        var player = player(helper); firstSteps(player);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 32);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 16);
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 0);
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 1);
        var aspects = new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10);
        var catalyst = new ItemStack(Items.GLOWSTONE_DUST);
        helper.assertTrue(CrucibleRecipes.find(catalyst, aspects, player) == null, "Nitor opened before stage 3");
        KnowledgeStore.recordCraft(player, new ItemStack(AlchemyModule.CRUCIBLE_ITEM.get()));
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 2), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(!KnowledgeStore.get(player).isResearchCompleteStrict("UNLOCKALCHEMY") && CrucibleRecipes.find(catalyst, aspects, player) != null, "Nitor requires completed alchemy and causes a deadlock");
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 3), ResearchProgression.Result.MISSING_REQUIREMENTS);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(pos);
        CompoundTag prepared = new CompoundTag(); prepared.putInt("Heat", 200); prepared.putInt("Water", 1000); aspects.writeToNBT(prepared); crucible.load(prepared);
        helper.assertTrue(crucible.consume(catalyst, player) && catalyst.isEmpty() && KnowledgeStore.get(player).hasCraft("thaumcraft:nitor"), "Real Nitor craft failed to write proof");
        result(helper, ResearchProgression.advance(player, "UNLOCKALCHEMY", 3), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchProgression.advance(player, "BASEALCHEMY", 0), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchProgression.advance(player, "ALUMENTUM", 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("ALUMENTUM"), "Opening alumentum unlocked the recipe without paying");
        result(helper, ResearchProgression.advance(player, "ALUMENTUM", 1), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(KnowledgeStore.get(player).save());
        helper.assertTrue(loaded.isResearchCompleteStrict("ALUMENTUM") && loaded.isResearchCompleteStrict("BASEALCHEMY") && loaded.knowsResearch("PORT_ALCHEMY") && loaded.hasCraft("thaumcraft:nitor"), "Canonical completion did not persist/enable residual lessons");
        helper.assertTrue(ResearchCatalog.get("PORT_BRASS").parents().stream().allMatch(loaded::knowsResearch), "Canonical base alchemy blocked existing brass prerequisites");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressPacketRoundTripBookGuardAndLegacyShortcutGuard(GameTestHelper helper) {
        var player = player(helper);
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        result(helper, ResearchNetwork.processAdvance(player, "FIRSTSTEPS", 0), ResearchProgression.Result.NO_BOOK);
        helper.assertTrue(KnowledgeStore.get(player).researchStage("FIRSTSTEPS") == 0, "No-book packet changed research");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var sent = new ResearchNetwork.Advance("FIRSTSTEPS", 0);
            ResearchNetwork.Advance.encode(sent, buffer);
            var decoded = ResearchNetwork.Advance.decode(buffer);
            helper.assertTrue(decoded.equals(sent) && buffer.readableBytes() == 0, "Progress packet changed key/expected stage");
            result(helper, ResearchNetwork.processAdvance(player, decoded.key(), decoded.expectedStage()), ResearchProgression.Result.STARTED);
            result(helper, ResearchNetwork.processAdvance(player, decoded.key(), decoded.expectedStage()), ResearchProgression.Result.STALE);
        } finally { buffer.release(); }
        result(helper, ResearchNetwork.processDiscover(player, "PORT_START"), ResearchProgression.Result.UNSUPPORTED);
        helper.assertTrue(!KnowledgeStore.get(player).researchKeys().contains("PORT_START"), "New player bypassed canonical stages through legacy packets");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        result(helper, ResearchNetwork.processDiscover(player, "PORT_BRASS"), ResearchProgression.Result.NO_BOOK);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void residualLessonDoesNotTurnNewPlayerIntoLegacyProfile(GameTestHelper helper) {
        var player = player(helper); firstSteps(player);
        var state = KnowledgeStore.get(player);
        // Only tallow remains a temporary lesson; metallurgy now uses the original staged entry.
        state.setResearchStage("UNLOCKALCHEMY", 4);
        state.setResearchStage("BASEALCHEMY", 2);
        var aspects = new AspectList();
        for (Aspect primal : Aspect.getPrimalAspects()) aspects.add(primal, 1);
        for (int i = 0; i < 5; i++) KnowledgeStore.recordScan(player, "test:residual_" + i, aspects);
        state.addKnowledge(KnowledgeType.OBSERVATION, "BASICS", -state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        result(helper, ResearchNetwork.processDiscover(player, "PORT_TALLOW"), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.knowsResearch("HEDGEALCHEMY@1"), "Remaining lesson lost its recipe");
        result(helper, ResearchNetwork.processDiscover(player, "PORT_BRASS"), ResearchProgression.Result.UNSUPPORTED);
        result(helper, ResearchNetwork.processDiscover(player, "PORT_THAUMIUM"), ResearchProgression.Result.UNSUPPORTED);
        result(helper, ResearchNetwork.processDiscover(player, "PORT_ALUMENTUM"), ResearchProgression.Result.UNSUPPORTED);
        result(helper, ResearchNetwork.processDiscover(player, "PORT_START"), ResearchProgression.Result.UNSUPPORTED);
        result(helper, ResearchNetwork.processAdvance(player, "ALUMENTUM", 0), ResearchProgression.Result.STARTED);
        result(helper, ResearchNetwork.processAdvance(player, "ALUMENTUM", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(!state.knowsResearch("ALUMENTUM"), "Residual lesson bypassed the canonical knowledge payment");
        helper.assertTrue(!ResearchProgression.legacyLessonAvailable(PlayerKnowledge.load(state.save()), "PORT_ALUMENTUM"), "Reload enabled legacy shortcut");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 16);
        result(helper, ResearchNetwork.processAdvance(player, "ALUMENTUM", 1), ResearchProgression.Result.COMPLETE);
        helper.succeed();
    }
}
