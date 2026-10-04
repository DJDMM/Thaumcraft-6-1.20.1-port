package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** Actual committed arcane craft facts and exact, atomic BETA26 stage payments. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EssentiaProgressionGameTests {
    private static final String[] PRIMALS = {"aer", "terra", "ignis", "aqua", "ordo", "perditio"};
    private static final String[] SECONDARIES = {"vacuos", "lux", "motus", "gelum", "vitreus", "metallum", "victus", "mortuus", "potentia", "permutatio"};
    private static final String[] PHIALS = {"vitium", "vinculum", "alienis", "alkimia"};
    private EssentiaProgressionGameTests() {}

    @GameTest(template = "empty")
    public static void essentiaParentsRequireCanonicalStagesAndCrystalPaymentsAreAtomic(GameTestHelper helper) {
        var player = player(helper);
        var state = KnowledgeStore.get(player);
        state.discover("PORT_ALUMENTUM"); state.discover("PORT_THAUMIUM");
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 0), ResearchProgression.Result.LOCKED);
        complete(state, "ALUMENTUM");
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 2);
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 0), ResearchProgression.Result.STARTED);
        for (int i = 0; i < PRIMALS.length - 1; i++) player.getInventory().setItem(i, crystal(PRIMALS[i], 2));
        int xp = player.totalExperience;
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        for (int i = 0; i < PRIMALS.length - 1; i++) helper.assertTrue(player.getInventory().getItem(i).getCount() == 2, "Partial crystal set was consumed");
        player.getInventory().setItem(40, crystal("perditio", 2));
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(player.totalExperience == xp && state.researchStage("ESSENTIASMELTER") == 1, "Missing/offhand sample rewarded or advanced");
        // Compatibility accepts the old exact primal item without permitting an offhand payment.
        player.getInventory().setItem(5, new ItemStack(WorldModule.VIS_CRYSTALS.get("perditio").get(), 2));
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 1), ResearchProgression.Result.ADVANCED);
        for (int i = 0; i < PRIMALS.length; i++) helper.assertTrue(player.getInventory().getItem(i).getCount() == 1, "Primal sample count changed");
        helper.assertTrue(player.getInventory().getItem(40).getCount() == 2, "Offhand was consumed");
        for (int i = 0; i < SECONDARIES.length; i++) player.getInventory().setItem(i + 9, crystal(SECONDARIES[i], 2));
        ItemStack mixed = crystal("vacuos", 2);
        new AspectList().add(Aspect.VOID, 1).add(Aspect.LIGHT, 1).writeToNBT(mixed.getOrCreateTag());
        player.getInventory().setItem(9, mixed);
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        for (int i = 0; i < SECONDARIES.length; i++) helper.assertTrue(player.getInventory().getItem(i + 9).getCount() == 2, "Mixed crystal spent other samples");
        player.getInventory().setItem(9, crystal("vacuos", 2));
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 2), ResearchProgression.Result.ADVANCED);
        for (int i = 0; i < SECONDARIES.length; i++) helper.assertTrue(player.getInventory().getItem(i + 9).getCount() == 1, "Secondary sample count changed");
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 2), ResearchProgression.Result.STALE);
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(loaded.researchStage("ESSENTIASMELTER") == 3 && !loaded.isResearchCompleteStrict("ESSENTIASMELTER")
                && !loaded.isResearchCompleteStrict("WARDEDJARS") && loaded.knowsResearch("METALLURGY@2"), "Reload changed branch progress/legacy access");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paidSmelterCraftUnlocksAlembicAndItsWardedJarSiblingOnlyAfterFinalPayment(GameTestHelper helper) {
        var player = player(helper);
        var state = KnowledgeStore.get(player);
        complete(state, "ALUMENTUM"); state.setResearchStage("METALLURGY", 2);
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 0), ResearchProgression.Result.STARTED);
        var bench = bench(helper, player);
        smelterInputs(bench); setVis(helper, bench, 100);
        bench.setItem(10, crystal("ignis", 2));
        CompoundTag before = bench.saveWithoutMetadata();
        helper.assertTrue(bench.craft(player).isEmpty() && before.equals(bench.saveWithoutMetadata()), "Stage-one player manufactured a smelter");
        for (int i = 0; i < PRIMALS.length; i++) player.getInventory().setItem(i, crystal(PRIMALS[i], 1));
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 1), ResearchProgression.Result.ADVANCED);
        // ConfigRecipes explicitly gates at @2, although the book first lists the recipe at stage 3.
        ItemStack smelter = bench.craft(player);
        helper.assertTrue(smelter.is(item("smelter_basic")) && state.hasCraft("thaumcraft:smelter_basic")
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 50 && bench.getItem(10).getCount() == 1,
                "Paid @2 smelter did not produce original craft evidence/cost");
        for (int i = 0; i < SECONDARIES.length; i++) player.getInventory().setItem(i, crystal(SECONDARIES[i], 1));
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 2), ResearchProgression.Result.ADVANCED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 19);
        int xp = player.totalExperience;
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 3), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 19 && player.totalExperience == xp
                && !state.isResearchCompleteStrict("WARDEDJARS"), "Incomplete multi-knowledge payment opened jars or charged observations");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 36);
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 3), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("ESSENTIASMELTER") == 5 && state.isResearchCompleteStrict("WARDEDJARS")
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 3
                && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 4 && player.totalExperience == xp + 10,
                "Final prose skip, paid raw remainder or automatic jar sibling differs");
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 3), ResearchProgression.Result.STALE);
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(loaded.isResearchCompleteStrict("WARDEDJARS") && loaded.hasCraft("thaumcraft:smelter_basic"), "Real manufacture/sibling did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void owningSmelterDoesNotReplaceManufactureFactOrSpendFinalKnowledge(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        complete(state, "ALUMENTUM"); state.setResearchStage("METALLURGY", 2); state.setResearchStage("ESSENTIASMELTER", 3);
        player.getInventory().setItem(0, new ItemStack(item("smelter_basic")));
        state.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 16); state.addKnowledge(KnowledgeType.THEORY, "ALCHEMY", 32);
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTER", 3), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(!state.hasCraft("thaumcraft:smelter_basic") && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 32
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 16 && player.getInventory().getItem(0).getCount() == 1,
                "Inventory possession replaced manufacture or spent a failed stage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tubesRequireAllFourExactPhialsAndBothObservationsBeforeAnyDebit(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        result(helper, ResearchProgression.advance(player, "TUBES", 0), ResearchProgression.Result.LOCKED);
        complete(state, "ESSENTIASMELTER"); complete(state, "WARDEDJARS");
        result(helper, ResearchProgression.advance(player, "TUBES", 0), ResearchProgression.Result.STARTED);
        state.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 19);
        for (int i = 0; i < PHIALS.length; i++) player.getInventory().setItem(i, CatalogModule.aspectStack("phial_filled", Aspect.getAspect(PHIALS[i]), 10).copyWithCount(2));
        result(helper, ResearchProgression.advance(player, "TUBES", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        state.addKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE", 20);
        player.getInventory().getItem(3).getOrCreateTag().getList("Aspects", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("amount", 1);
        result(helper, ResearchProgression.advance(player, "TUBES", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        player.getInventory().setItem(3, CatalogModule.aspectStack("phial_filled", Aspect.ALCHEMY, 10).copyWithCount(2));
        ItemStack flux = player.getInventory().removeItemNoUpdate(0); player.getInventory().setItem(40, flux);
        result(helper, ResearchProgression.advance(player, "TUBES", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 19
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 20 && player.getInventory().getItem(1).getCount() == 2,
                "Bad/offhand phial charged a partial stage");
        player.getInventory().setItem(0, player.getInventory().removeItemNoUpdate(40));
        result(helper, ResearchProgression.advance(player, "TUBES", 1), ResearchProgression.Result.COMPLETE);
        for (int i = 0; i < PHIALS.length; i++) helper.assertTrue(player.getInventory().getItem(i).getCount() == 1, "Phial sample did not consume exactly one");
        helper.assertTrue(state.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 3
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 4 && state.researchStage("TUBES") == 3
                && PlayerKnowledge.load(state.save()).isResearchCompleteStrict("TUBES"), "Tube payment/final prose/reload changed");
        result(helper, ResearchProgression.advance(player, "TUBES", 1), ResearchProgression.Result.STALE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookItemTemplatesAreDetachedAndPreviewChecksTheSameMainInventoryPayment(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        complete(state, "ALUMENTUM"); state.setResearchStage("METALLURGY", 2); state.setResearchStage("ESSENTIASMELTER", 1);
        var templates = ResearchProgression.requiredItems("ESSENTIASMELTER", 1);
        helper.assertTrue(templates.size() == 6, "Book has no six exact primal templates");
        templates.get(0).getOrCreateTag().putString("Mutation", "not-persisted");
        helper.assertTrue(!ResearchProgression.requiredItems("ESSENTIASMELTER", 1).get(0).getTag().contains("Mutation"), "Caller mutated loaded research templates");
        for (int i = 0; i < PRIMALS.length; i++) player.getInventory().setItem(i, crystal(PRIMALS[i], 1));
        helper.assertTrue(ResearchProgression.canAdvance(state, ResearchCatalog.get("ESSENTIASMELTER"), player.getInventory()), "Exact main-inventory preview is unavailable");
        ItemStack last = player.getInventory().removeItemNoUpdate(5); player.getInventory().setItem(40, last);
        helper.assertTrue(!ResearchProgression.canAdvance(state, ResearchCatalog.get("ESSENTIASMELTER"), player.getInventory()), "Preview counted an offhand sample");
        result(helper, ResearchProgression.advance(player, "ESSENTIASMELTERTHAUMIUM", 0), ResearchProgression.Result.LOCKED);
        for (String key : new String[]{"ESSENTIASMELTERVOID", "IMPROVEDSMELTING", "IMPROVEDSMELTING2", "BELLOWS", "INFUSIONANCIENT", "BASEELDRITCH"}) {
            result(helper, ResearchProgression.advance(player, key, 0), ResearchProgression.Result.UNSUPPORTED);
            helper.assertTrue(!state.isResearchCompleteStrict(key), "Unported parent was marked complete: " + key);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) { return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "essentia_progress")); }
    private static void complete(PlayerKnowledge state, String key) { state.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1); }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) { helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual); }
    private static net.minecraft.world.item.Item item(String id) { return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)); }
    private static ItemStack crystal(String aspect, int count) { return AspectCrystalItem.create(Aspect.getAspect(aspect), count); }
    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
    }
    private static void setVis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench, float amount) { AuraManager.drainVis(helper.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false); AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), amount); }
    private static void smelterInputs(ArcaneWorkbenchBlockEntity bench) {
        for (int slot : new int[]{0, 2}) bench.setItem(slot, new ItemStack(item("plate_brass")));
        bench.setItem(1, new ItemStack(thaumcraft.alchemy.AlchemyModule.CRUCIBLE_ITEM.get()));
        for (int slot : new int[]{3, 5, 6, 7, 8}) bench.setItem(slot, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        bench.setItem(4, new ItemStack(net.minecraft.world.item.Items.FURNACE));
    }
}
