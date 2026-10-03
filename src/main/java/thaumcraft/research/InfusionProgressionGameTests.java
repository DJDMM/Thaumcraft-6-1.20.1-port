package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionProgressionGameTests {
    @GameTest(template = "empty")
    public static void infusionPaysExactMainInventoryPhialAndKnowledgeAtomically(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "BASEINFUSION");
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "INFUSION", 20);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 38);
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 2));
        player.getInventory().setItem(10, new ItemStack(Items.FEATHER, 2));
        ItemStack mixed = phial();
        new AspectList().add(Aspect.AIR, 10).add(Aspect.WATER, 1).writeToNBT(mixed.getOrCreateTag());
        player.getInventory().setItem(11, mixed);
        int experience = player.totalExperience;
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION") == 20
                && state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 38
                && player.getInventory().getItem(9).getCount() == 2 && player.totalExperience == experience,
                "Mixed phial charged a partial payment");
        player.getInventory().setItem(11, ItemStack.EMPTY); player.setItemInHand(InteractionHand.OFF_HAND, phial());
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        player.getInventory().setItem(11, phial());
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.researchStage("INFUSION") == 2 && !state.isResearchCompleteStrict("INFUSION")
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION") == 4
                && state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 6
                && player.getInventory().getItem(9).getCount() == 1 && player.getInventory().getItem(10).getCount() == 1
                && player.getInventory().getItem(11).isEmpty() && !player.getOffhandItem().isEmpty(),
                "Original16/32 costs, sample counts, or stage2 gate changed");
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 1), ResearchProgression.Result.STALE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matrixCraftProofAndEmptyConclusionAreDistinctFromOwningTheBlock(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "BASEINFUSION");
        state.setResearchStage("INFUSION", 2);
        ItemStack matrix = item("infusion_matrix"); player.getInventory().setItem(9, matrix);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(!state.hasCraft("thaumcraft:infusion_matrix"), "Possessing a matrix manufactured craft proof");
        KnowledgeStore.recordCraft(player, matrix);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 2), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(state.researchStage("INFUSION") == 4 && loaded.isResearchCompleteStrict("INFUSION")
                && loaded.hasCraft("thaumcraft:infusion_matrix") && player.getInventory().getItem(9).getCount() == 1,
                "Craft proof was consumed, final empty chapter remained unpaid, or state was not persistent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void auromancyHeightFactsOnlyApplyDuringStageOneAndRealCraftsUnlockGauntlet(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "UNLOCKALCHEMY");
        player.setPos(0, 9, 0); InfusionProgressionEvents.checkPeriodicFacts(player);
        helper.assertTrue(!state.knowsResearch("m_deepdown"), "Height fact appeared before the lesson");
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKAUROMANCY", 0), ResearchProgression.Result.STARTED);
        player.setPos(0, 10, 0); InfusionProgressionEvents.checkPeriodicFacts(player);
        helper.assertTrue(!state.knowsResearch("m_deepdown"), "Deep height threshold became inclusive");
        player.setPos(0, 9.99, 0); InfusionProgressionEvents.checkPeriodicFacts(player);
        double upper = player.serverLevel().getMaxBuildHeight() * .4;
        player.setPos(0, upper, 0); InfusionProgressionEvents.checkPeriodicFacts(player);
        helper.assertTrue(!state.knowsResearch("m_uphigh"), "High threshold became inclusive");
        player.setPos(0, upper + .01, 0); InfusionProgressionEvents.checkPeriodicFacts(player);
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKAUROMANCY", 1), ResearchProgression.Result.ADVANCED);
        player.getInventory().setItem(9, item("vis_resonator")); player.getInventory().setItem(10, item("caster_basic"));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKAUROMANCY", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordCraft(player, item("vis_resonator"));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKAUROMANCY", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordCraft(player, item("caster_basic"));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKAUROMANCY", 2), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.isResearchCompleteStrict("UNLOCKAUROMANCY") && !state.isResearchCompleteStrict("BASEAUROMANCY"),
                "Gauntlet lesson invented focus research completion");
        var later = player(helper); KnowledgeStore.get(later).setResearchStage("UNLOCKAUROMANCY", 2);
        later.setPos(0, 0, 0); InfusionProgressionEvents.checkPeriodicFacts(later);
        helper.assertTrue(!KnowledgeStore.get(later).knowsResearch("m_deepdown"), "Height fact appeared after stage1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movementFactsReadExactStrictServerStatisticsWithoutCompletingBoots(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        var statistics = player.getStats();
        statistics.setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 160000);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.SPRINT_ONE_CM), 80000);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.SWIM_ONE_CM), 8000);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.JUMP), 500);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        for (String key : new String[]{"m_walker", "m_runner", "m_swimmer", "m_jumper"})
            helper.assertTrue(!state.knowsResearch(key), "Movement threshold became inclusive: " + key);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 160001);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.SPRINT_ONE_CM), 80001);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.SWIM_ONE_CM), 8001);
        statistics.setValue(player, Stats.CUSTOM.get(Stats.JUMP), 501);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        var saved = state.save(); var loaded = PlayerKnowledge.load(saved);
        for (String key : new String[]{"m_walker", "m_runner", "m_swimmer", "m_jumper"})
            helper.assertTrue(loaded.isResearchCompleteStrict(key), "Server statistic fact did not persist: " + key);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        helper.assertTrue(saved.equals(state.save()) && !state.isResearchCompleteStrict("BOOTSTRAVELLER")
                && state.researchStage("BOOTSTRAVELLER") == 0, "Repeated stats created rewards or fictional boots research");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boostersRequireCompletedInfusionAndSpendExactlyTwoTheoryUnits(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        state.setResearchStage("INFUSION", 2);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSIONBOOST", 0), ResearchProgression.Result.LOCKED);
        complete(state, "INFUSION");
        result(helper, ResearchNetwork.processAdvance(player, "INFUSIONBOOST", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 63);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSIONBOOST", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 4);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSIONBOOST", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 3
                && state.researchStage("INFUSIONBOOST") == 3, "Two-unit theory cost or empty conclusion changed");
        result(helper, ResearchNetwork.processAdvance(player, "INFUSIONBOOST", 1), ResearchProgression.Result.STALE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsupportedFocusAndDescendantBranchesKeepCanonicalParentsAndNoBookCannotPay(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        complete(state, "INFUSION"); complete(state, "UNLOCKAUROMANCY");
        helper.assertTrue(ResearchCatalog.get("RECHARGEPEDESTAL").parents().equals(java.util.List.of("BASEAUROMANCY"))
                && ResearchCatalog.get("BOOTSTRAVELLER").parents().contains("RECHARGEPEDESTAL"), "An unported prerequisite was deleted");
        for (String key : new String[]{"FORTRESSMASK", "INFUSIONENCHANTMENT", "RUNICSHIELDING",
                "FOCUSADVANCED", "FOCUSGREATER", "INFUSIONSTABLE", "INFUSIONANCIENT", "INFUSIONELDRITCH"}) {
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.UNSUPPORTED);
            helper.assertTrue(state.researchStage(key) == 0 && !state.isResearchCompleteStrict(key), "Unported entry was marked completed: " + key);
        }
        var bare = player(helper); complete(KnowledgeStore.get(bare), "BASEINFUSION");
        bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        result(helper, ResearchNetwork.processAdvance(bare, "INFUSION", 0), ResearchProgression.Result.NO_BOOK);
        helper.assertTrue(KnowledgeStore.get(bare).researchStage("INFUSION") == 0, "No-book request opened a research stage");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "infusion_progress"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        return player;
    }
    private static void complete(PlayerKnowledge knowledge, String key) { knowledge.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1); }
    private static ItemStack item(String id) { return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id))); }
    private static ItemStack phial() {
        ItemStack stack = item("phial_filled"); new AspectList().add(Aspect.AIR, 10).writeToNBT(stack.getOrCreateTag()); return stack;
    }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
}
