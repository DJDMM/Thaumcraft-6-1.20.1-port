package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.ResearchCategories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ScanAspect is a separate registered handler; generic identity dedup cannot suppress its discovery. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ScanAspectGameTests {
    private static final List<String> BONUS_CATEGORIES = List.of("BASICS", "AUROMANCY", "ALCHEMY");
    private ScanAspectGameTests() {}

    @GameTest(template = "empty")
    public static void originalCategoryFormulaAndTwoIndependentAspectBonusesAreBothPaid(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        h.assertTrue(store.recordScan(id, "item:minecraft:coal", new AspectList().add(Aspect.FIRE, 10).add(Aspect.ENERGY, 10)),
                "Fresh positive-aspect specimen was rejected");
        Map<String, Integer> expected = Map.of("BASICS", 5, "AUROMANCY", 2, "ALCHEMY", 2,
                "ARTIFICE", 4, "INFUSION", 0, "GOLEMANCY", 0, "ELDRITCH", 0);
        for (String category : ResearchCategories.keys()) h.assertTrue(
                store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, category) == expected.get(category),
                "Generic weights plus released +1 per-new-aspect bonuses changed: " + category);
        h.assertTrue(store.get(id).isResearchKnown("!ignis") && store.get(id).isResearchKnown("!potentia")
                        && store.get(id).scanCount() == 1,
                "Generic discovery omitted independently registered aspect research flags");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changedCompositionOnCreditedIdentityPaysOnlyOneNewAspectBonus(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        store.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.AIR, 1));
        int[] before = balances(store, id);
        h.assertTrue(store.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.CRYSTAL, 50)),
                "Known specimen identity suppressed a new ScanAspect handler");
        assertBonusDelta(h, store, id, before, 1);
        h.assertTrue(store.get(id).scanCount() == 1 && store.get(id).isResearchKnown("!vitreus"),
                "NBT composition change created a second generic scan or omitted its aspect proof");
        CompoundTag complete = store.get(id).save();
        h.assertTrue(!store.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.CRYSTAL, 1))
                        && complete.equals(store.get(id).save()),
                "Already discovered aspect quantity changes repaid its one-time bonus");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anotherIdentityWithKnownAspectsPaysGenericFormulaWithoutAspectBonuses(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        AspectList found = new AspectList().add(Aspect.FIRE, 10);
        store.recordScan(id, "item:test:first", found);
        int[] before = balances(store, id);
        h.assertTrue(store.recordScan(id, "item:test:second", found), "Different specimen identity was deduplicated");
        int[] after = balances(store, id);
        for (int i = 0; i < after.length; i++) h.assertTrue(after[i] - before[i] == ResearchCategories.observationGain(ResearchCategories.keys().get(i), found),
                "Known aspect received its discovery bonus again: " + ResearchCategories.keys().get(i));
        h.assertTrue(store.get(id).scanCount() == 2, "Distinct generic identities did not remain distinct");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void compoundAspectsHaveNoPreviouslyDiscoveredComponentGate(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        h.assertTrue(!store.get(id).knowsAspect(Aspect.MAGIC.getComponents()[0])
                        && !store.get(id).knowsAspect(Aspect.MAGIC.getComponents()[1]),
                "Fresh fixture already knew compound components");
        h.assertTrue(store.recordScan(id, "item:test:compound", new AspectList().add(Aspect.MAGIC, 10))
                        && store.get(id).isResearchKnown("!praecantatio"),
                "A TC4 component prerequisite was imposed on the released TC6 ScanAspect handler");
        h.assertTrue(store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, "BASICS") == 1
                        && store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 6
                        && store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 5,
                "Compound observation formula or three independent bonus categories changed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realNbtCrystalVariantRemainsHighlightedUntilItsAspectIsActuallyScanned(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack first = AspectCrystalItem.create(Aspect.AIR, 32);
        p.setItemInHand(InteractionHand.OFF_HAND, first);
        scan(p);
        var knowledge = KnowledgeStore.get(p);
        h.assertTrue(knowledge.isResearchKnown("!aer") && knowledge.scanCount() == 1,
                "Actual first crystal scan omitted generic or aspect knowledge");
        int[] before = balances(KnowledgeStore.of(p.serverLevel()), p.getUUID());
        ItemStack next = AspectCrystalItem.create(Aspect.EARTH, 64);
        h.assertTrue(ThaumometerItem.itemTarget(first, Vec3.ZERO).key().equals(ThaumometerItem.itemTarget(next, Vec3.ZERO).key()),
                "Crystal NBT variants acquired distinct generic identities");
        p.setItemInHand(InteractionHand.OFF_HAND, next);
        CompoundTag pending = knowledge.save();
        CompoundTag sample = next.save(new CompoundTag());
        var hover = ScanningNetwork.capture(p);
        h.assertTrue(hover.target() != null && !hover.target().scanned() && !knowledge.isResearchKnown("!terra")
                        && pending.equals(knowledge.save()),
                "New aspect hover was not eligible or granted its proof without RMB");
        scan(p);
        assertBonusDelta(h, KnowledgeStore.of(p.serverLevel()), p.getUUID(), before, 1);
        h.assertTrue(knowledge.isResearchKnown("!terra") && knowledge.scanCount() == 1
                        && sample.equals(next.save(new CompoundTag())) && ScanningNetwork.capture(p).target().scanned(),
                "Actual variant scan omitted its aspect, duplicated identity or consumed the specimen");
        CompoundTag finished = knowledge.save();
        scan(p);
        h.assertTrue(finished.equals(knowledge.save()), "Repeated NBT crystal scan repaid knowledge");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleNewAspectsOnOneCreditedSpecimenEachPayOnce(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        store.recordScan(id, "item:test:sample", new AspectList().add(Aspect.AIR, 1));
        int[] before = balances(store, id);
        h.assertTrue(store.recordScan(id, "item:test:sample", new AspectList().add(Aspect.AIR, 100)
                        .add(Aspect.FIRE, 2).add(Aspect.WATER, 80).add(Aspect.EARTH, 1)),
                "Credited generic identity suppressed multiple unknown aspect handlers");
        assertBonusDelta(h, store, id, before, 3);
        h.assertTrue(store.get(id).scanCount() == 1 && store.get(id).isResearchKnown("!ignis")
                        && store.get(id).isResearchKnown("!aqua") && store.get(id).isResearchKnown("!terra"),
                "One matching specimen omitted one of its independently registered aspect facts");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedKnowledgeRetainsGenericCreditWhileAdmittingNewAspectFacts(GameTestHelper h) {
        KnowledgeStore source = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        source.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.AIR, 1));
        KnowledgeStore restored = KnowledgeStore.load(source.save(new CompoundTag()));
        int[] before = balances(restored, id);
        h.assertTrue(restored.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.ENTROPY, 1)),
                "Save/load suppressed an unseen aspect on a credited specimen");
        assertBonusDelta(h, restored, id, before, 1);
        h.assertTrue(restored.get(id).isResearchKnown("!aer") && restored.get(id).isResearchKnown("!perditio")
                        && restored.get(id).scanCount() == 1,
                "Save/load lost aspect flags or credited the generic specimen twice");
        CompoundTag beforeRepeat = restored.get(id).save();
        h.assertTrue(!restored.recordScan(id, "item:thaumcraft:crystal_essence", new AspectList().add(Aspect.AIR, 1))
                        && beforeRepeat.equals(restored.get(id).save()), "Saved old aspect was credited again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidAndNonPositiveScanInputsNeverPartiallyDiscoverAnything(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        CompoundTag before = store.get(id).save();
        AspectList invalid = new AspectList();
        invalid.aspects.put(Aspect.FIRE, 0);
        invalid.aspects.put(Aspect.WATER, -3);
        invalid.aspects.put(null, 100);
        h.assertTrue(!store.recordScan(id, "item:test:invalid", invalid)
                        && !store.recordScan(id, "", new AspectList().add(Aspect.FIRE, 1))
                        && !store.recordScan(id, "item:test:empty", new AspectList())
                        && !store.recordScan(id, "item:test:null", null)
                        && before.equals(store.get(id).save()),
                "Invalid/non-positive scan input created credit, aspect flags or observation balances");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void combinedGenericAndAspectRewardOverflowRejectsTheEntireScan(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        store.addKnowledge(id, KnowledgeType.OBSERVATION, "BASICS", Integer.MAX_VALUE - 3);
        CompoundTag before = store.get(id).save();
        // Fire10: generic BASICS3 plus newly discovered Ignis1 exceeds the available three units.
        h.assertTrue(!store.recordScan(id, "item:test:overflow", new AspectList().add(Aspect.FIRE, 10))
                        && before.equals(store.get(id).save()),
                "Overflow in the combined reward left a generic credit, aspect flag or partial category payment");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aspectOnlyOverflowOnCreditedSpecimenRejectsEveryNewFactAndCategory(GameTestHelper h) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = UUID.randomUUID();
        store.recordScan(id, "item:test:known", new AspectList().add(Aspect.AIR, 1));
        int old = store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY");
        store.addKnowledge(id, KnowledgeType.OBSERVATION, "ALCHEMY", Integer.MAX_VALUE - old);
        CompoundTag before = store.get(id).save();
        h.assertTrue(!store.recordScan(id, "item:test:known", new AspectList().add(Aspect.FIRE, 1).add(Aspect.WATER, 1))
                        && before.equals(store.get(id).save()) && !store.get(id).isResearchKnown("!ignis")
                        && !store.get(id).isResearchKnown("!aqua"),
                "Aspect-only overflow partially wrote knowledge or independently registered aspect facts");
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ScanAspect"));
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
        p.setYRot(0);
        p.setXRot(0);
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        return p;
    }

    private static void scan(ServerPlayer p) {
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
    }

    private static int[] balances(KnowledgeStore store, UUID id) {
        return ResearchCategories.keys().stream().mapToInt(category -> store.get(id).rawKnowledge(KnowledgeType.OBSERVATION, category)).toArray();
    }

    private static void assertBonusDelta(GameTestHelper h, KnowledgeStore store, UUID id, int[] before, int count) {
        int[] after = balances(store, id);
        for (int i = 0; i < after.length; i++) {
            String category = ResearchCategories.keys().get(i);
            h.assertTrue(after[i] - before[i] == (BONUS_CATEGORIES.contains(category) ? count : 0),
                    "Aspect-only discovery repaid generic Observation or changed its released category bonus: " + category);
        }
    }
}
