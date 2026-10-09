package thaumcraft.research;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.List;
import java.util.Set;

/** Migrate known identities without refunding, revoking or repeating earned scan rewards. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ScanIdentityMigrationGameTests {
    private ScanIdentityMigrationGameTests() {}

    @GameTest(template = "empty")
    public static void creditedCompositionKeyCollapsesAndPreservesBalancesAcrossReload(GameTestHelper h) {
        String old = "item:minecraft:coal|ignis=10|potentia=10";
        CompoundTag tag = profile(List.of(old), List.of(old));
        var loaded = PlayerKnowledge.load(tag);
        h.assertTrue(loaded.scanKeys().equals(Set.of("item:minecraft:coal")) && loaded.hasScanned(old)
                        && loaded.hasScanned("item:minecraft:coal"),
                "Old credited composition was lost or remained a separate generic identity");
        h.assertTrue(!loaded.recordScan("item:minecraft:coal", new AspectList().add(Aspect.FIRE, 10))
                        && !loaded.recordScan("item:minecraft:coal|ignis=20", new AspectList().add(Aspect.FIRE, 20)),
                "Migrated scan or a changed composition repeated its generic Observation credit");
        assertBalances(h, loaded);
        var saved = loaded.save();
        h.assertTrue(saved.getList("CreditedScans", Tag.TAG_STRING).size() == 1
                        && saved.getList("CreditedScans", Tag.TAG_STRING).getString(0).equals("item:minecraft:coal"),
                "Normalized credit was not written in the saved profile");
        var twice = PlayerKnowledge.load(saved);
        h.assertTrue(twice.scanCount() == 1 && !twice.recordScan("item:minecraft:coal", new AspectList().add(Aspect.FIRE, 10)),
                "Reload after migration forgot the prior credit");
        assertBalances(h, twice);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleCreditedVariantsCollapseWithoutRecalculatingOldRewards(GameTestHelper h) {
        var old = List.of("item:minecraft:iron_sword|metallum=10|instrumentum=5",
                "item:minecraft:iron_sword|metallum=10|instrumentum=5|praecantatio=3",
                "entity:minecraft:creeper|herba=10|potentia=5",
                "entity:minecraft:creeper|herba=10|potentia=15");
        var loaded = PlayerKnowledge.load(profile(old, old));
        h.assertTrue(loaded.scanCount() == 2 && loaded.scanKeys().equals(Set.of("item:minecraft:iron_sword", "entity:minecraft:creeper")),
                "Item enchantment or powered-entity composition remained a second generic identity");
        h.assertTrue(!loaded.recordScan("item:minecraft:iron_sword|praecantatio=50", new AspectList().add(Aspect.MAGIC, 50))
                        && !loaded.recordScan("entity:minecraft:creeper", new AspectList().add(Aspect.ENERGY, 20)),
                "Collapsed credited variants allowed another reward");
        assertBalances(h, loaded);
        var twice = PlayerKnowledge.load(loaded.save());
        h.assertTrue(twice.scanCount() == 2 && twice.save().getList("CreditedScans", Tag.TAG_STRING).size() == 2,
                "Collapsed credits did not survive another save/load");
        assertBalances(h, twice);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allLegacyPrimalIdsNormalizeToOneCrystalEssenceCredit(GameTestHelper h) {
        var old = List.of("item:thaumcraft:vis_crystal_aer|aer=1", "item:thaumcraft:vis_crystal_ignis|ignis=1",
                "item:thaumcraft:vis_crystal_aqua|aqua=1", "item:thaumcraft:vis_crystal_terra|terra=1",
                "item:thaumcraft:vis_crystal_ordo|ordo=1", "item:thaumcraft:vis_crystal_perditio|perditio=1",
                "item:thaumcraft:crystal_essence|praecantatio=1");
        var loaded = PlayerKnowledge.load(profile(old, old));
        h.assertTrue(loaded.scanKeys().equals(Set.of("item:thaumcraft:crystal_essence")),
                "The old six primal aliases retained multiple original crystal_essence identities");
        for (String key : old) h.assertTrue(loaded.hasScanned(key)
                        && !loaded.recordScan(key, new AspectList().add(Aspect.AIR, 1)),
                "Legacy crystal alias was unknown or credited again after normalization: " + key);
        h.assertTrue(!loaded.recordScan("item:thaumcraft:crystal_essence", new AspectList().add(Aspect.WATER, 1)),
                "New tagged original crystal repeated a legacy credit");
        assertBalances(h, loaded);
        var twice = PlayerKnowledge.load(loaded.save());
        h.assertTrue(twice.scanCount() == 1 && twice.hasScanned("item:thaumcraft:vis_crystal_perditio"),
                "Legacy crystal normalization did not persist");
        assertBalances(h, twice);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flattenedMetadataFamiliesRetainDistinctModernIds(GameTestHelper h) {
        var old = List.of("item:minecraft:coal|ignis=10", "item:minecraft:charcoal|ignis=10",
                "item:minecraft:granite|terra=5", "item:minecraft:diorite|terra=5",
                "item:minecraft:oak_log|herba=20", "item:minecraft:spruce_log|herba=20");
        var loaded = PlayerKnowledge.load(profile(old, old));
        h.assertTrue(loaded.scanCount() == 6, "Old nondamageable metadata families collapsed different modern item IDs");
        for (String key : old) h.assertTrue(loaded.hasScanned(PlayerKnowledge.scanIdentity(key))
                        && !loaded.recordScan(key, new AspectList().add(Aspect.EARTH, 5)),
                "Flattened metadata identity was lost or recredited: " + key);
        h.assertTrue(!PlayerKnowledge.scanIdentity(old.get(0)).equals(PlayerKnowledge.scanIdentity(old.get(1)))
                        && !PlayerKnowledge.scanIdentity(old.get(2)).equals(PlayerKnowledge.scanIdentity(old.get(3)))
                        && !PlayerKnowledge.scanIdentity(old.get(4)).equals(PlayerKnowledge.scanIdentity(old.get(5))),
                "The normalizer generalized genuinely distinct metadata forms");
        assertBalances(h, loaded);
        h.assertTrue(PlayerKnowledge.load(loaded.save()).scanCount() == 6, "Metadata forms collapsed on the second load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void opaqueOldHashIsPreservedWithoutInventingItsLostItemIdentity(GameTestHelper h) {
        String opaque = "scan:64f83d79e80ef431b612b0e9d3aa8227c2f530db9156136f5a9580d43e1f7142";
        var loaded = PlayerKnowledge.load(profile(List.of(opaque), List.of(opaque)));
        h.assertTrue(loaded.scanKeys().equals(Set.of(opaque)) && !loaded.hasScanned("item:minecraft:enchanted_book")
                        && PlayerKnowledge.scanIdentity(opaque).equals(opaque)
                        && !loaded.recordScan(opaque, new AspectList().add(Aspect.MAGIC, 10)),
                "Opaque old composition hash was deleted, repeated or assigned an invented item identity");
        assertBalances(h, loaded);
        var twice = PlayerKnowledge.load(loaded.save());
        h.assertTrue(twice.scanKeys().equals(Set.of(opaque)) && !twice.hasScanned("item:minecraft:enchanted_book"),
                "Opaque history changed or acquired a fabricated identity after reload");
        assertBalances(h, twice);
        h.succeed();
    }

    private static CompoundTag profile(List<String> scans, List<String> credited) {
        var knowledge = new PlayerKnowledge();
        for (int i = 0; i < ResearchCategories.keys().size(); i++) {
            String category = ResearchCategories.keys().get(i);
            knowledge.addKnowledge(KnowledgeType.OBSERVATION, category, 37 + i);
            knowledge.addKnowledge(KnowledgeType.THEORY, category, 75 + i);
        }
        knowledge.discover("f_arrow");
        // An existing scanner profile already stored discovered-aspect facts.
        // They must survive normalization without being retroactively paid again.
        for (Aspect aspect : Aspect.aspects.values()) knowledge.discoverAspect(aspect);
        CompoundTag result = knowledge.save();
        result.put("Scans", strings(scans));
        result.put("CreditedScans", strings(credited));
        return result;
    }

    private static ListTag strings(List<String> values) {
        ListTag result = new ListTag();
        values.forEach(value -> result.add(StringTag.valueOf(value)));
        return result;
    }

    private static void assertBalances(GameTestHelper h, PlayerKnowledge knowledge) {
        for (int i = 0; i < ResearchCategories.keys().size(); i++) {
            String category = ResearchCategories.keys().get(i);
            h.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION, category) == 37 + i
                            && knowledge.rawKnowledge(KnowledgeType.THEORY, category) == 75 + i,
                    "Identity migration altered an already earned balance: " + category);
        }
        h.assertTrue(knowledge.isResearchCompleteStrict("f_arrow") && knowledge.knowsAspect(Aspect.FIRE),
                "Identity migration removed a real special scan proof or discovered aspect");
    }
}
