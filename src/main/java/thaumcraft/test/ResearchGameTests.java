package thaumcraft.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchEntry;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchGameTests {
    @GameTest(template = "empty")
    public static void scansAreUniquePersistentAndPlayerSpecific(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID first = new UUID(1L, 2L), second = new UUID(3L, 4L);
        AspectList aspects = new AspectList().add(Aspect.AIR, 5).add(Aspect.MAGIC, 2);
        helper.assertTrue(store.recordScan(first, "item:minecraft:stone", aspects), "First scan was refused");
        helper.assertTrue(!store.recordScan(first, "item:minecraft:stone", aspects), "Duplicate scan awarded credit");
        helper.assertTrue(!store.recordScan(first, "item:empty", new AspectList()), "Aspect-free object awarded credit");
        helper.assertTrue(!store.recordScan(first, "item:negative", new AspectList().add(Aspect.AIR, -1)), "Negative aspect awarded credit");
        helper.assertTrue(store.get(first).scanCount() == 1, "Scan count changed after duplicate");
        helper.assertTrue(store.get(second).scanCount() == 0, "Player knowledge leaked");
        helper.assertTrue(store.get(first).knowsAspect(Aspect.AIR) && store.get(first).knowsAspect(Aspect.MAGIC), "Discovered aspects missing");
        helper.assertTrue(store.discoverResearch(first, "PORT_START"), "Initial lesson unavailable");
        KnowledgeStore loaded = KnowledgeStore.load(store.save(new CompoundTag()));
        helper.assertTrue(loaded.get(first).hasScanned("item:minecraft:stone"), "Scanned key did not survive NBT round trip");
        helper.assertTrue(loaded.get(first).knowsAspect(Aspect.MAGIC), "Aspect did not survive NBT round trip");
        helper.assertTrue(loaded.get(first).knowsResearch("PORT_START"), "Research did not survive NBT round trip");
        helper.assertTrue(!loaded.recordScan(first, "item:minecraft:stone", aspects), "Duplicate credited after reload");
        helper.assertTrue(store.isDirty(), "Knowledge data not marked dirty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void researchRequiresEvidenceAndNeverUnlocksArchive(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID player = new UUID(7L, 8L);
        helper.assertTrue(!store.discoverResearch(player, "PORT_THAUMIUM"), "Research unlocked without prerequisites");
        helper.assertTrue(!store.discoverResearch(player, "PORT_SCAN"), "Scan research unlocked without scanning");
        helper.assertTrue(!store.discoverResearch(player, "INFUSION"), "Unimplemented archival research unlocked");
        helper.assertTrue(!store.discoverResearch(player, "METALLURGY"), "Archive metallurgy unlocked");
        helper.assertTrue(!store.discoverResearch(player, "unknown"), "Unknown research unlocked");
        store.discoverResearch(player, "PORT_START");
        AspectList aspects = new AspectList().add(Aspect.AIR, 1).add(Aspect.FIRE, 1).add(Aspect.WATER, 1)
                .add(Aspect.EARTH, 1).add(Aspect.ORDER, 1).add(Aspect.ENTROPY, 1);
        store.recordScan(player, "item:first", aspects);
        helper.assertTrue(store.discoverResearch(player, "PORT_SCAN"), "Valid scan lesson not unlocked");
        helper.assertTrue(!store.discoverResearch(player, "PORT_ALCHEMY"), "Alchemy ignored scan threshold");
        for (int i = 2; i <= 10; i++) store.recordScan(player, "item:sample_" + i, aspects);
        helper.assertTrue(store.discoverResearch(player, "PORT_ALCHEMY"), "Alchemy requirements did not work");
        helper.assertTrue(store.get(player).knowsResearch("BASEALCHEMY"), "Supported recipe alias missing");
        helper.assertTrue(!store.discoverResearch(player, "PORT_THAUMIUM"), "Thaumium skipped brass prerequisite");
        helper.assertTrue(store.discoverResearch(player, "PORT_BRASS"), "Brass lesson unavailable");
        helper.assertTrue(store.discoverResearch(player, "PORT_THAUMIUM"), "Thaumium lesson unavailable");
        helper.assertTrue(store.get(player).knowsResearch("METALLURGY@1") && store.get(player).knowsResearch("METALLURGY@2"), "Stage recipe aliases failed");
        helper.assertTrue(!store.get(player).knowsResearch("METALLURGY@3") && !store.get(player).knowsResearch("METALLURGY"), "Unsupported stages marked complete");
        helper.assertTrue(!store.discoverResearch(player, "PORT_BRASS"), "Already completed lesson accepted again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalResearchGraphIsPresentButExplicitlyUnsupported(GameTestHelper helper) {
        helper.assertTrue(ResearchCatalog.entries().stream().filter(entry -> !entry.supported()).count() == 148, "Original research count changed");
        helper.assertTrue(ResearchCatalog.get("PORT_ALUMENTUM").supported(), "Implemented alumentum progression missing");
        var first = ResearchCatalog.get("FIRSTSTEPS");
        helper.assertTrue(first != null && first.stages().size() == 3 && first.parents().contains("!gotthaumonomicon"), "Original stages/prerequisites lost");
        helper.assertTrue(first.column() == 0 && first.row() == 0 && first.hasMeta("ROUND") && first.hasMeta("SPIKY"), "Root layout or frame metadata lost");
        helper.assertTrue(first.icons().equals(List.of("thaumcraft:textures/items/thaumonomicon.png")), "Original texture icon changed");
        helper.assertTrue(first.siblings().equals(List.of("KNOWLEDGETYPES", "!gotdream")), "Sibling research data lost");
        helper.assertTrue(first.stages().get(0).requirements().stream().anyMatch(text -> text.contains("arcane_workbench")), "Incompatible original craft requirement silently dropped");
        helper.assertTrue(ResearchCatalog.categories().equals(List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH", "PORT")), "Original category order changed");
        helper.assertTrue(KnowledgeStore.of(helper.getLevel()) == KnowledgeStore.of(helper.getLevel().getServer().overworld()), "Knowledge store is not anchored in overworld");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void graphLinksResolveWithoutGrantingResearch(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID player = new UUID(91L, 92L);
        var bolt = ResearchCatalog.get("FOCUSBOLT");
        helper.assertTrue(bolt.column() == 7 && bolt.row() == 0, "Original nonzero research coordinate lost");
        helper.assertTrue(bolt.icons().equals(List.of("focus:thaumcraft.BOLT")), "Focus icon encoded as an ordinary item");
        helper.assertTrue(bolt.parents().equals(List.of("FOCUSPROJECTILE@2")), "Rendering normalization overwrote the stage prerequisite");
        helper.assertTrue(ResearchCatalog.graphParents(bolt).equals(List.of(ResearchCatalog.get("FOCUSPROJECTILE"))), "Staged parent does not resolve to its graph node");
        helper.assertTrue(ResearchCatalog.graphParentKey("~TUBES").equals("TUBES"), "Hidden parent prefix was not normalized");
        helper.assertTrue(ResearchCatalog.graphParentKey("!gotthaumonomicon").equals("!gotthaumonomicon"), "Special discovery marker was stripped");
        helper.assertTrue(ResearchCatalog.graphParents(ResearchCatalog.get("FIRSTSTEPS")).isEmpty(), "Unrepresented discovery created a fake graph node");
        for (ResearchEntry entry : ResearchCatalog.entries()) {
            for (String raw : entry.parents()) {
                if (raw.startsWith("~") || raw.indexOf('@') >= 0) {
                    helper.assertTrue(ResearchCatalog.get(ResearchCatalog.graphParentKey(raw)) != null, "Broken normalized parent: " + raw);
                }
            }
            if (!entry.supported()) {
                helper.assertTrue(!entry.canDiscover(store.get(player)) && !store.discoverResearch(player, entry.key()),
                        "Graph metadata unlocked archival research: " + entry.key());
            }
        }
        helper.assertTrue(!store.get(player).knowsResearch("FOCUSPROJECTILE@2") && !store.get(player).knowsResearch("FOCUSBOLT"), "Resolving graph edges granted research");
        var positions = new HashSet<String>();
        for (ResearchEntry entry : ResearchCatalog.entries()) {
            if (entry.supported()) {
                helper.assertTrue(entry.category().equals("PORT") && !entry.icons().isEmpty(), "Native lesson lost its category or icon");
                helper.assertTrue(positions.add(entry.column() + ":" + entry.row()), "Native graph nodes overlap");
            }
        }
        helper.assertTrue(positions.size() == 8 && ResearchCatalog.get("PORT_ALUMENTUM").supported(), "Native alumentum lesson lost during enrichment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void researchPagesKeepAddendaRecipesAndRequirementsSeparate(GameTestHelper helper) {
        var archive = ResearchCatalog.entries().stream().filter(entry -> !entry.supported()).toList();
        helper.assertTrue(archive.stream().mapToInt(entry -> entry.stages().size()).sum() == 271, "Addenda mixed into original stage count");
        helper.assertTrue(archive.stream().mapToInt(entry -> entry.addenda().size()).sum() == 16, "Original addenda missing");
        var ore = ResearchCatalog.get("ORE");
        helper.assertTrue(ore.stages().size() == 1 && ore.addenda().size() == 3 && ore.hasMeta("HIDDEN"), "Hidden ore entry lost its separate addenda");
        helper.assertTrue(ore.addenda().get(2).text().equals("research.ORE.crystal")
                && ore.addenda().get(2).requiredResearch().equals(List.of("!ORECRYSTAL")), "Addendum condition was not retained");
        var first = ResearchCatalog.get("FIRSTSTEPS");
        helper.assertTrue(first.stages().get(1).recipes().equals(List.of("thaumcraft:thaumometer", "thaumcraft:salismundusfake")), "Original recipe links changed");
        var auromancy = ResearchCatalog.get("BASEAUROMANCY").stages().get(1);
        helper.assertTrue(auromancy.requiredResearch().equals(List.of("f_onfire"))
                && auromancy.requirements().contains("required_craft: thaumcraft:wand_workbench"), "Stage evidence and craft requirements merged or dropped");
        helper.assertTrue(ResearchCatalog.get("FOCUSRIFT").stages().get(0).warp() == 2, "Stage warp metadata lost");
        helper.succeed();
    }
}
