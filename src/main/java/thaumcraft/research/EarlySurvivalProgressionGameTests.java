package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EarlySurvivalProgressionGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "early_test"));
    }

    /** Prior start-chain operations have dedicated tests; these fixtures start at completed basic alchemy. */
    private static PlayerKnowledge alchemy(ServerPlayer player) {
        PlayerKnowledge state = KnowledgeStore.get(player);
        state.setResearchStage("UNLOCKALCHEMY", ResearchCatalog.get("UNLOCKALCHEMY").stages().size() + 1);
        state.setResearchStage("BASEALCHEMY", 2);
        return state;
    }

    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }

    private static CrucibleBlockEntity crucible(GameTestHelper helper, AspectList aspects) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(pos);
        CompoundTag prepared = new CompoundTag();
        prepared.putInt("Heat", 200);
        prepared.putInt("Water", 1000);
        aspects.writeToNBT(prepared);
        crucible.load(prepared);
        return crucible;
    }

    @GameTest(template = "empty")
    public static void originalMetallurgyRequiresCommittedBrassAndThaumiumAndSurvivesReload(GameTestHelper helper) {
        var player = player(helper);
        result(helper, ResearchProgression.advance(player, "METALLURGY", 0), ResearchProgression.Result.LOCKED);
        var state = alchemy(player);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 21);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 35);
        result(helper, ResearchProgression.advance(player, "METALLURGY", 0), ResearchProgression.Result.STARTED);
        result(helper, ResearchProgression.advance(player, "METALLURGY", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 21, "Missing craft spent observations");
        helper.assertTrue(CrucibleRecipes.find(new ItemStack(Items.IRON_INGOT),
                new AspectList().add(Aspect.MAGIC, 5).add(Aspect.EARTH, 5), player) == null, "Thaumium opened before stage 2");
        var brass = crucible(helper, new AspectList().add(Aspect.TOOL, 5));
        helper.assertTrue(brass.consume(new ItemStack(Items.IRON_INGOT), player), "Actual brass transmutation failed");
        helper.assertTrue(state.hasCraft("thaumcraft:ingot_brass") && !state.hasCraft("thaumcraft:ingot_thaumium"), "Metal metadata craft proof was confused");
        result(helper, ResearchProgression.advance(player, "METALLURGY", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.researchStage("METALLURGY") == 2 && state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 5,
                "Stage 1 payment or pre-craft stage access differs from BETA26");
        result(helper, ResearchProgression.advance(player, "METALLURGY", 1), ResearchProgression.Result.STALE);
        result(helper, ResearchProgression.advance(player, "METALLURGY", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 35, "Missing thaumium spent theory");
        var thaumium = crucible(helper, new AspectList().add(Aspect.MAGIC, 5).add(Aspect.EARTH, 5));
        helper.assertTrue(thaumium.consume(new ItemStack(Items.IRON_INGOT), player), "Actual thaumium transmutation failed");
        result(helper, ResearchProgression.advance(player, "METALLURGY", 2), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("METALLURGY") == 4 && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 3,
                "Theory payment or final prose stage skip is incorrect");
        int experience = player.totalExperience;
        result(helper, ResearchProgression.advance(player, "METALLURGY", 2), ResearchProgression.Result.STALE);
        helper.assertTrue(player.totalExperience == experience && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 3,
                "Replayed completion paid or rewarded again");
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(loaded.isResearchCompleteStrict("METALLURGY") && loaded.hasCraft("thaumcraft:ingot_thaumium")
                && loaded.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 3, "Metallurgy facts/payment did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void artificeAndInfusionUnlocksRequireActualAspectFactsAndAtomicKnowledge(GameTestHelper helper) {
        var player = player(helper);
        var state = alchemy(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKARTIFICE", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 2);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ARTIFICE", 19);
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKARTIFICE", 0), ResearchProgression.Result.STARTED);
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKARTIFICE", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordScan(player, "test:artifice_senses", new AspectList().add(Aspect.SENSES, 1));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKARTIFICE", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") >= 19, "Incomplete aspect facts spent knowledge");
        KnowledgeStore.recordScan(player, "test:artifice_machine", new AspectList().add(Aspect.MECHANISM, 1));
        int beforeArtifice = state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE");
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKARTIFICE", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == beforeArtifice - 16,
                "Artifice did not spend one observation");
        result(helper, ResearchNetwork.processAdvance(player, "BASEARTIFICE", 0), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKINFUSION", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "INFUSION", 20);
        KnowledgeStore.recordScan(player, "test:infusion_aura", new AspectList().add(Aspect.AURA, 1));
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKINFUSION", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        KnowledgeStore.recordScan(player, "test:infusion_magic", new AspectList().add(Aspect.MAGIC, 1));
        int beforeInfusion = state.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION");
        result(helper, ResearchNetwork.processAdvance(player, "UNLOCKINFUSION", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION") == beforeInfusion - 16,
                "Infusion unlock did not spend one observation");
        result(helper, ResearchNetwork.processAdvance(player, "BASEINFUSION", 0), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "INFUSION", 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(PlayerKnowledge.load(state.save()).isResearchCompleteStrict("UNLOCKINFUSION"), "Infusion branch unlock did not survive save");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyMetalUnlocksPersistWithoutOpeningOriginalResearchOrNewPlayerShortcuts(GameTestHelper helper) {
        var player = player(helper);
        var state = alchemy(player);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        helper.assertTrue(!ResearchProgression.legacyLessonAvailable(state, "PORT_BRASS")
                && !ResearchProgression.legacyLessonAvailable(state, "PORT_THAUMIUM"), "New player sees superseded metallurgy lessons");
        result(helper, ResearchNetwork.processDiscover(player, "PORT_BRASS"), ResearchProgression.Result.UNSUPPORTED);
        state.discover("PORT_TALLOW");
        helper.assertTrue(!ResearchProgression.legacyLessonAvailable(PlayerKnowledge.load(state.save()), "PORT_BRASS"),
                "Residual tallow lesson turned a new player into an old profile");
        var old = new PlayerKnowledge();
        old.discover("PORT_BRASS");
        old.discover("PORT_THAUMIUM");
        var loaded = PlayerKnowledge.load(old.save());
        helper.assertTrue(loaded.knowsResearch("METALLURGY@1") && loaded.knowsResearch("METALLURGY@2")
                && !loaded.isResearchCompleteStrict("METALLURGY@2") && !loaded.isResearchCompleteStrict("METALLURGY"),
                "Old recipe access was lost or converted to fictional original progress");
        helper.assertTrue(ResearchProgression.legacyLessonAvailable(loaded, "PORT_BRASS"), "Existing old profile lost its legacy lesson view");
        var mixed = new PlayerKnowledge();
        mixed.setResearchStage("UNLOCKALCHEMY", ResearchCatalog.get("UNLOCKALCHEMY").stages().size() + 1);
        mixed.discover("PORT_THAUMIUM");
        helper.assertTrue(!ResearchProgression.canStart(mixed, "UNLOCKARTIFICE"), "Old recipe alias bypassed a canonical stage prerequisite");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalItemMetadataMapsToDistinctModernResearchRequirements(GameTestHelper helper) {
        helper.assertTrue(LegacyResearchItems.resolve("thaumcraft:ingot;1;2").equals(new ResourceLocation("thaumcraft", "ingot_brass")), "Brass mapping incorrect");
        helper.assertTrue(LegacyResearchItems.resolve("thaumcraft:ingot;1;0").equals(new ResourceLocation("thaumcraft", "ingot_thaumium")), "Thaumium mapping incorrect");
        helper.assertTrue(LegacyResearchItems.resolve("thaumcraft:phial;1;1;{Aspects:[]}").equals(new ResourceLocation("thaumcraft", "phial_filled")), "Filled phial metadata mapping incorrect");
        helper.assertTrue(LegacyResearchItems.resolve("thaumcraft:nitor;1;4").equals(new ResourceLocation("thaumcraft", "nitor")), "Yellow nitor mapping changed");
        boolean rejected = false;
        try { LegacyResearchItems.resolve("thaumcraft:ingot;1;99"); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unknown metadata silently became a different item");
        helper.succeed();
    }
}
