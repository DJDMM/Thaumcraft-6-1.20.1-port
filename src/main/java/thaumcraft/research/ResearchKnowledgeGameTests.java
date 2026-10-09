package thaumcraft.research;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchKnowledgeGameTests {
    @GameTest(template = "empty")
    public static void scansAwardWeightedKnowledgeInClosedCategoriesOnce(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID player = new UUID(400L, 1L);
        PlayerKnowledge knowledge = store.get(player);
        AspectList found = new AspectList().add(Aspect.AIR, 8).add(Aspect.MAGIC, 4);
        helper.assertTrue(!ResearchCategories.categoryUnlocked(knowledge, "ALCHEMY"), "Fresh alchemy tab is unlocked");
        helper.assertTrue(store.recordScan(player, "item:test:weighted", found), "Valid scan rejected");
        helper.assertTrue(store.isDirty(), "Successful scan was not marked for saving");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 4, "Basics weighted formula plus two first-aspect bonuses changed");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 6, "Auromancy category weights or first-aspect bonuses changed");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 4, "Closed alchemy tab lost weighted knowledge or first-aspect bonuses");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION") == 4, "Infusion category weights changed");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "ELDRITCH") == 2, "Eldritch category weights changed");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 0
                && knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "GOLEMANCY") == 0, "Unrelated categories received knowledge");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 0, "Scanning awarded theory knowledge");
        store.setDirty(false);
        helper.assertTrue(!store.recordScan(player, "item:test:weighted", found) && !store.isDirty(), "Duplicate scan changed saved data");
        KnowledgeStore loaded = KnowledgeStore.load(store.save(new CompoundTag()));
        helper.assertTrue(!loaded.recordScan(player, "item:test:weighted", found), "Reload credited the scan twice");
        helper.assertTrue(loaded.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 6, "Knowledge was not persisted");
        helper.assertTrue(loaded.get(new UUID(400L, 2L)).rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 0, "Knowledge leaked to another player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyScansReceiveOneCreditWithoutInflatingTheirCount(GameTestHelper helper) {
        UUID player = new UUID(401L, 1L);
        CompoundTag oldPlayer = new CompoundTag();
        oldPlayer.putInt("Version", 1);
        oldPlayer.putUUID("Player", player);
        ListTag scans = new ListTag();
        scans.add(StringTag.valueOf("scan:legacy_hash_with_no_embedded_aspects"));
        oldPlayer.put("Scans", scans);
        ListTag research = new ListTag();
        research.add(StringTag.valueOf("PORT_NITOR"));
        oldPlayer.put("Research", research);
        ListTag players = new ListTag();
        players.add(oldPlayer);
        CompoundTag oldStore = new CompoundTag();
        oldStore.put("Players", players);
        KnowledgeStore loaded = KnowledgeStore.load(oldStore);
        helper.assertTrue(loaded.get(player).scanCount() == 1, "Version 1 scan was discarded");
        helper.assertTrue(loaded.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 0, "Migration fabricated knowledge from a scan count");
        AspectList found = new AspectList().add(Aspect.AIR, 8);
        helper.assertTrue(loaded.recordScan(player, "scan:legacy_hash_with_no_embedded_aspects", found), "Legacy revisit did not receive its first knowledge credit");
        helper.assertTrue(loaded.isDirty() && loaded.get(player).scanCount() == 1, "Legacy credit duplicated the scan or did not save");
        KnowledgeStore twice = KnowledgeStore.load(loaded.save(new CompoundTag()));
        helper.assertTrue(!twice.recordScan(player, "scan:legacy_hash_with_no_embedded_aspects", found), "Migrated scan received another credit after reload");
        helper.assertTrue(twice.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 3, "Migrated knowledge changed on reload");
        helper.assertTrue(twice.get(player).knowsResearch("UNLOCKALCHEMY@3")
                && !twice.get(player).isResearchKnown("UNLOCKALCHEMY")
                && !twice.get(player).isResearchCompleteStrict("UNLOCKALCHEMY@3"), "Legacy recipe access fabricated a canonical stage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void researchStagesAndRecipeCompatibilityRemainIndependent(GameTestHelper helper) {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.discover("PORT_START");
        knowledge.discover("PORT_ALCHEMY");
        knowledge.discover("PORT_ALUMENTUM");
        helper.assertTrue(knowledge.knowsResearch("FIRSTSTEPS@2") && !knowledge.isResearchCompleteStrict("FIRSTSTEPS@2")
                && !knowledge.isResearchKnown("FIRSTSTEPS") && knowledge.researchStage("FIRSTSTEPS") == 0, "Legacy thaumometer recipe access fabricated initial progress");
        helper.assertTrue(knowledge.knowsResearch("BASEALCHEMY") && knowledge.knowsResearch("ALUMENTUM"), "Old recipes lost their access");
        helper.assertTrue(!knowledge.isResearchCompleteStrict("BASEALCHEMY")
                && !knowledge.isResearchKnown("ALUMENTUM") && knowledge.researchStage("BASEALCHEMY") == 0, "PORT lesson became canonical research");
        knowledge.setResearchStage("FIRSTSTEPS", 2);
        helper.assertTrue(knowledge.isResearchKnown("FIRSTSTEPS") && knowledge.knowsResearch("~FIRSTSTEPS@2"), "Active stage did not unlock its recipe");
        helper.assertTrue(!knowledge.isResearchCompleteStrict("FIRSTSTEPS")
                && !knowledge.knowsResearch("FIRSTSTEPS") && !knowledge.knowsResearch("FIRSTSTEPS@3"), "Opening a stage marked research complete");
        knowledge.setResearchStage("FIRSTSTEPS", 4);
        helper.assertTrue(knowledge.isResearchCompleteStrict("FIRSTSTEPS"), "Terminal stage was not complete");
        knowledge.setResearchStage("UNLOCKALCHEMY", 3);
        helper.assertTrue(knowledge.knowsResearch("UNLOCKALCHEMY@3")
                && !ResearchCategories.categoryUnlocked(knowledge, "ALCHEMY"), "Stage recipe and category completion were conflated");
        knowledge.setResearchStage("UNLOCKALCHEMY", 4);
        helper.assertTrue(ResearchCategories.categoryUnlocked(knowledge, "ALCHEMY"), "Completed unlock did not open the tab");
        PlayerKnowledge nativePlayer = new PlayerKnowledge();
        nativePlayer.setResearchStage("BASEALCHEMY", 2);
        helper.assertTrue(nativePlayer.knowsResearch("PORT_ALCHEMY") && !nativePlayer.researchKeys().contains("PORT_ALCHEMY")
                && !nativePlayer.isResearchCompleteStrict("PORT_ALCHEMY"), "Remaining prototype lesson bridge saved false PORT progress");
        PlayerKnowledge roundTrip = PlayerKnowledge.load(knowledge.save());
        helper.assertTrue(roundTrip.researchStage("FIRSTSTEPS") == 4 && roundTrip.researchStage("UNLOCKALCHEMY") == 4, "Canonical stages were not persisted");
        helper.assertTrue(!roundTrip.knowsResearch("FIRSTSTEPS@broken") && !roundTrip.knowsResearch("FIRSTSTEPS@0"), "Invalid stage requirement unlocked research");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rawKnowledgePreservesRemaindersAndRejectsOverflow(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID player = new UUID(402L, 1L);
        PlayerKnowledge knowledge = store.get(player);
        helper.assertTrue(KnowledgeType.OBSERVATION.units() == 16 && KnowledgeType.THEORY.units() == 32, "TC6 raw knowledge units changed");
        store.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 17);
        store.addKnowledge(player, KnowledgeType.THEORY, "BASICS", 33);
        helper.assertTrue(knowledge.completedKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 1
                && knowledge.completedKnowledge(KnowledgeType.THEORY, "BASICS") == 1, "Fractional knowledge was rounded up");
        helper.assertTrue(store.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", -16)
                && store.addKnowledge(player, KnowledgeType.THEORY, "BASICS", -32), "Valid knowledge cost was rejected");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 1
                && knowledge.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 1, "Spending a point discarded the raw remainder");
        helper.assertTrue(!store.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", -2), "Debit produced a negative balance");
        KnowledgeStore loaded = KnowledgeStore.load(store.save(new CompoundTag()));
        helper.assertTrue(loaded.get(player).completedKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 0
                && loaded.get(player).completedKnowledge(KnowledgeType.THEORY, "BASICS") == 0, "Reload replenished spent knowledge points");
        helper.assertTrue(!loaded.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", -16)
                && !loaded.addKnowledge(player, KnowledgeType.THEORY, "BASICS", -32)
                && !loaded.isDirty(), "Exhausted full-point costs changed the reloaded state");
        helper.assertTrue(loaded.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 1
                && loaded.get(player).rawKnowledge(KnowledgeType.THEORY, "BASICS") == 1, "Rejected costs consumed the raw remainder");
        helper.assertTrue(loaded.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", -1)
                && loaded.addKnowledge(player, KnowledgeType.THEORY, "BASICS", -1) && loaded.isDirty(), "Exact final debit was not saved");
        loaded.setDirty(false);
        helper.assertTrue(!loaded.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", -1)
                && !loaded.addKnowledge(player, KnowledgeType.THEORY, "BASICS", -1) && !loaded.isDirty(), "Zero balances accepted another debit");
        helper.assertTrue(store.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", Integer.MAX_VALUE), "Largest valid raw balance was rejected");
        helper.assertTrue(!store.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 1)
                && knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == Integer.MAX_VALUE, "Credit overflowed the raw balance");
        helper.assertTrue(!store.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", Integer.MIN_VALUE), "Extreme debit underflowed the raw balance");
        int gain = ResearchCategories.observationGain("ALCHEMY", new AspectList().add(Aspect.ALCHEMY, Integer.MAX_VALUE));
        helper.assertTrue(gain == 80265, "Aspect weighting overflowed before conversion to double");
        helper.assertTrue(!store.recordScan(player, "item:test:overflow", new AspectList().add(Aspect.MAGIC, 4))
                && !knowledge.hasScanned("item:test:overflow")
                && knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 0, "Overflowing scan partially awarded or consumed its first credit");
        helper.assertTrue(!store.addKnowledge(player, KnowledgeType.THEORY, "PORT", 32), "Prototype category received canonical knowledge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedSaveDataCannotManufactureKnowledgeOrStages(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2);
        ListTag scans = new ListTag();
        scans.add(StringTag.valueOf("item:test:credited"));
        scans.add(StringTag.valueOf(" "));
        tag.put("Scans", scans);
        ListTag credited = new ListTag();
        credited.add(StringTag.valueOf("item:test:credited"));
        credited.add(StringTag.valueOf("item:test:orphan"));
        tag.put("CreditedScans", credited);
        ListTag aspects = new ListTag();
        aspects.add(StringTag.valueOf("aer"));
        aspects.add(StringTag.valueOf("not_an_aspect"));
        tag.put("Aspects", aspects);
        ListTag crafts = new ListTag();
        crafts.add(StringTag.valueOf("thaumcraft:arcane_workbench"));
        crafts.add(StringTag.valueOf("invalid item"));
        tag.put("Crafts", crafts);
        CompoundTag stages = new CompoundTag();
        stages.putDouble("FIRSTSTEPS", 3.9);
        stages.putInt("UNLOCKALCHEMY", Integer.MAX_VALUE);
        stages.putInt("BASEALCHEMY", 2);
        stages.putInt("UNKNOWN_RESEARCH", 1);
        tag.put("ResearchStages", stages);
        CompoundTag observation = new CompoundTag();
        observation.putInt("BASICS", 17);
        observation.putInt("AUROMANCY", -1);
        observation.putLong("ALCHEMY", (1L << 32) + 16);
        observation.putDouble("ARTIFICE", Double.POSITIVE_INFINITY);
        observation.putString("GOLEMANCY", "32");
        observation.putInt("UNKNOWN_CATEGORY", 32);
        CompoundTag raw = new CompoundTag();
        raw.put("OBSERVATION", observation);
        raw.put("UNKNOWN_TYPE", observation.copy());
        tag.put("Knowledge", raw);

        PlayerKnowledge knowledge = PlayerKnowledge.load(tag);
        helper.assertTrue(knowledge.researchStage("BASEALCHEMY") == 2 && knowledge.isResearchCompleteStrict("BASEALCHEMY"), "Valid stage was lost while sanitizing NBT");
        helper.assertTrue(knowledge.researchStage("FIRSTSTEPS") == 0 && knowledge.researchStage("UNLOCKALCHEMY") == 0
                && knowledge.researchStage("UNKNOWN_RESEARCH") == 0, "Malformed stage data granted canonical progress");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 17
                && knowledge.completedKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 1, "Valid raw balance did not survive sanitization");
        for (String category : ResearchCategories.keys()) {
            if (!category.equals("BASICS")) helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, category) == 0, "Malformed knowledge granted a balance in " + category);
            helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.THEORY, category) == 0, "Unknown knowledge type became theory");
        }
        helper.assertTrue(knowledge.knowsAspect(Aspect.AIR) && !knowledge.isResearchCompleteStrict("!not_an_aspect"), "Unknown aspect data became an event fact");
        helper.assertTrue(knowledge.hasCraft("thaumcraft:arcane_workbench") && !knowledge.hasCraft("invalid item"), "Malformed craft ID was accepted");
        AspectList found = new AspectList().add(Aspect.AIR, 8);
        helper.assertTrue(knowledge.scanCount() == 1 && !knowledge.recordScan("item:test:credited", found), "Saved credited scan received another award");
        helper.assertTrue(knowledge.recordScan("item:test:orphan", found), "Orphan credit suppressed a first valid scan");
        helper.assertTrue(PlayerKnowledge.load(null).scanCount() == 0 && !KnowledgeStore.load(null).isDirty(), "Absent saved data was not handled safely");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftAndEventFactsPersistWithoutGrantingResearch(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID player = new UUID(403L, 1L);
        helper.assertTrue(store.recordCraft(player, "thaumcraft:arcane_workbench"), "Successful craft was not recorded");
        helper.assertTrue(store.isDirty(), "Successful craft was not marked for saving");
        store.setDirty(false);
        helper.assertTrue(!store.recordCraft(player, "thaumcraft:arcane_workbench")
                && !store.recordCraft(player, "invalid item") && !store.recordCraft(player, "nitor"), "Craft fact validation failed");
        helper.assertTrue(!store.isDirty(), "Duplicate or invalid crafts changed saved data");
        helper.assertTrue(store.recordFact(player, "!gotthaumonomicon"), "Pickup marker was not recorded");
        helper.assertTrue(store.isDirty(), "Successful event fact was not marked for saving");
        store.setDirty(false);
        helper.assertTrue(!store.recordFact(player, "!gotthaumonomicon") && !store.isDirty(), "Duplicate event fact changed saved data");
        store.get(player).discoverAspect(Aspect.AIR);
        KnowledgeStore loaded = KnowledgeStore.load(store.save(new CompoundTag()));
        PlayerKnowledge knowledge = loaded.get(player);
        helper.assertTrue(knowledge.hasCraft("thaumcraft:arcane_workbench")
                && knowledge.isResearchCompleteStrict("!gotthaumonomicon")
                && knowledge.isResearchCompleteStrict("~!aer"), "Craft, pickup, or aspect discovery did not persist");
        helper.assertTrue(!knowledge.isResearchKnown("FIRSTSTEPS") && knowledge.researchStage("FIRSTSTEPS") == 0, "Evidence itself completed a research stage");
        helper.assertTrue(!loaded.get(new UUID(403L, 2L)).hasCraft("thaumcraft:arcane_workbench"), "Craft fact leaked across players");
        helper.succeed();
    }
}
