package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.research.theory.TheoryModule;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryProgressionGameTests {
    @GameTest(template = "empty")
    public static void originalTheoryEntryNeedsBothCommittedCraftsAndSkipsFinalText(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "theory_progress"));
        PlayerKnowledge state = KnowledgeStore.get(player);
        helper.assertTrue(ResearchProgression.isImplemented("THEORYRESEARCH")
                && ResearchProgression.advance(player, "THEORYRESEARCH", 0) == ResearchProgression.Result.LOCKED, "Theory research ignored its parents");
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        helper.assertTrue(ResearchProgression.advance(player, "FIRSTSTEPS", 0) == ResearchProgression.Result.STARTED, "Initial siblings did not unlock");
        helper.assertTrue(ResearchProgression.advance(player, "THEORYRESEARCH", 0) == ResearchProgression.Result.STARTED, "Theory entry could not start after knowledge types");
        player.getInventory().add(new ItemStack(TheoryModule.SCRIBING_TOOLS.get()));
        player.getInventory().add(new ItemStack(TheoryModule.TABLE_ITEM.get()));
        helper.assertTrue(ResearchProgression.advance(player, "THEORYRESEARCH", 1) == ResearchProgression.Result.MISSING_REQUIREMENTS, "Owning the items fabricated craft proofs");
        KnowledgeStore.recordCraft(player, new ItemStack(TheoryModule.SCRIBING_TOOLS.get()));
        helper.assertTrue(ResearchProgression.advance(player, "THEORYRESEARCH", 1) == ResearchProgression.Result.MISSING_REQUIREMENTS, "One craft satisfied both requirements");
        KnowledgeStore.recordCraft(player, new ItemStack(TheoryModule.TABLE_ITEM.get()));
        int experience = player.totalExperience;
        helper.assertTrue(ResearchProgression.advance(player, "THEORYRESEARCH", 1) == ResearchProgression.Result.COMPLETE
                && state.researchStage("THEORYRESEARCH") == 3 && state.isResearchCompleteStrict("THEORYRESEARCH"), "Final theory text was not skipped on completion");
        helper.assertTrue(player.totalExperience == experience + 5
                && ResearchProgression.advance(player, "THEORYRESEARCH", 1) == ResearchProgression.Result.STALE
                && player.totalExperience == experience + 5, "Replayed proof granted experience twice");
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 0, "Crafting the table itself awarded a theory");
        helper.succeed();
    }
}
