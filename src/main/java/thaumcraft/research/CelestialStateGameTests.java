package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CelestialStateGameTests {
    @GameTest(template = "empty")
    public static void celestialQuotaSurvivesReloadAndIsBoundedPerUuid(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        long day = (long) Integer.MAX_VALUE + 20;
        for (int md = 0; md < 13; md++) {
            helper.assertTrue(store.recordCelestial(first, day, md), "Distinct celestial note was rejected");
            helper.assertTrue(!store.recordCelestial(first, day, md), "Duplicate celestial note passed");
        }
        KnowledgeStore restored = KnowledgeStore.load(store.save(new CompoundTag()));
        for (int md = 0; md < 13; md++) helper.assertTrue(restored.get(first).hasCelestial(day, md), "Quota was lost on reload");
        helper.assertTrue(restored.recordCelestial(second, day, 0) && !restored.get(second).hasCelestial(day, 1), "Player quotas leaked");
        helper.assertTrue(restored.recordCelestial(first, day + 1, 0)
                && !restored.get(first).hasCelestial(day + 1, 1)
                && !restored.get(first).hasCelestial(day, 0), "New day did not replace bounded quota");
        helper.assertTrue(!restored.recordCelestial(first, -1, 0) && !restored.recordCelestial(first, day, -1)
                && !restored.recordCelestial(first, day, 13), "Invalid quota input was accepted");
        helper.assertTrue(restored.get(first).researchKeys().isEmpty(), "Quota polluted research facts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialResearchPaysAllThreeObservationsOnce(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "sky_progress"));
        PlayerKnowledge state = KnowledgeStore.get(player);
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 0) == ResearchProgression.Result.LOCKED, "Missing parent was ignored");
        state.setResearchStage("THEORYRESEARCH", 3);
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 0) == ResearchProgression.Result.STARTED, "Celestial entry did not start");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 20);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ARTIFICE", 16);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", 15);
        int xp = player.totalExperience;
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 1) == ResearchProgression.Result.MISSING_REQUIREMENTS
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 20
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 16
                && player.totalExperience == xp, "Incomplete payment debited a category");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", 1);
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 1) == ResearchProgression.Result.COMPLETE
                && state.isResearchCompleteStrict("CELESTIALSCANNING")
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 4
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 0
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 0, "Original observation cost is wrong");
        helper.assertTrue(ResearchProgression.advance(player, "CELESTIALSCANNING", 1) == ResearchProgression.Result.STALE
                && player.totalExperience == xp + 5, "Replay granted XP or advanced twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void temporaryWarpClampsPersistsAndDecaysAtOriginalTickBoundary(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "sky_warp"));
        KnowledgeStore.addTemporaryWarp(player, Integer.MAX_VALUE);
        PlayerKnowledge state = KnowledgeStore.get(player);
        helper.assertTrue(state.temporaryWarp() == 500 && state.warpCounter() == 500, "Warp overflowed its cap");
        PlayerKnowledge restored = PlayerKnowledge.load(state.save());
        helper.assertTrue(restored.temporaryWarp() == 500 && restored.warpCounter() == 500, "Warp was lost on reload");
        player.tickCount = 1999;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(state.temporaryWarp() == 500, "Warp decayed before its tick boundary");
        player.tickCount = 2000;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        helper.assertTrue(state.temporaryWarp() == 500, "Warp decayed in START phase");
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(state.temporaryWarp() == 499 && state.warpCounter() == 500, "Temporary decay changed the activity counter");
        KnowledgeStore.addTemporaryWarp(player, Integer.MIN_VALUE);
        helper.assertTrue(state.temporaryWarp() == 0 && !ResearchEvents.decayTemporaryWarp(player), "Warp underflowed zero");
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("TemporaryWarp", -1); invalid.putInt("WarpCounter", Integer.MAX_VALUE);
        invalid.putLong("CelestialDay", 3); invalid.putInt("CelestialMask", 8192);
        PlayerKnowledge sanitized = PlayerKnowledge.load(invalid);
        helper.assertTrue(sanitized.temporaryWarp() == 0 && sanitized.warpCounter() == 500
                && !sanitized.hasCelestial(3, 0) && PlayerKnowledge.load(new CompoundTag()).temporaryWarp() == 0, "Malformed/old state was not sanitized");
        helper.succeed();
    }
}
