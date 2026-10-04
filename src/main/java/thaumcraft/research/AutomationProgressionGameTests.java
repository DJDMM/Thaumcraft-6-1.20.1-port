package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.api.aspects.*;
import thaumcraft.arcane.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.production.SmelterBlockEntity;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;

/** Parent stages are fixtures; target stages and physical payments use the actual book/recipe paths. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class AutomationProgressionGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private AutomationProgressionGameTests() {}
    private static ServerPlayer player(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "automation_research"));
        var pos = h.absolutePos(CENTER);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        return player;
    }
    private static void complete(PlayerKnowledge state, String key) {
        state.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1);
    }
    private static ItemStack item(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)));
    }
    private static void result(GameTestHelper h, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        h.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }

    @GameTest(template = "essentia_network")
    public static void thaumiumSmelterDropsObsoleteCraftRequirementButKeepsStageThreePlateAndTheoryPayment(GameTestHelper h) {
        var player = player(h); var state = KnowledgeStore.get(player);
        h.assertTrue(ResearchCatalog.get("ESSENTIASMELTERTHAUMIUM").parents().equals(List.of("TUBES", "METALLURGY@3")), "Changed original smelter parents");
        complete(state, "TUBES"); state.setResearchStage("METALLURGY", 2);
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 3);
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 0), ResearchProgression.Result.STARTED);
        var plates = item("plate_thaumium"); plates.setCount(2);
        plates.getOrCreateTag().putString("kept", "extra-root-tag"); player.getInventory().setItem(9, plates);
        var displayRows = ResearchBookRequirements.rows(ResearchCatalog.get("ESSENTIASMELTERTHAUMIUM").stages().get(0), state, player.getInventory());
        h.assertTrue(displayRows.stream().noneMatch(row -> row.kind() == ResearchBookRequirements.Kind.CRAFT)
                && displayRows.stream().anyMatch(row -> row.kind() == ResearchBookRequirements.Kind.ITEM && row.item().is(plates.getItem()) && row.required() == 1),
                "Book retained the obsolete craft or hid the actual thaumium plate payment");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 31);
        // The obsolete thaumcraft:metal;1;2 craft was discarded by BETA26. No replacement proof is invented.
        player.getInventory().setItem(10, new ItemStack(CatalogBlocks.block("metal_thaumium")));
        var before = state.save(); var inventory = player.getInventory().getItem(9).copy(); int xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        h.assertTrue(before.equals(state.save()) && ItemStack.matches(inventory, player.getInventory().getItem(9)) && xp == player.totalExperience,
                "31 theory partially paid");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 4);
        h.assertTrue(!state.hasCraft("thaumcraft:metal_thaumium") && !state.hasCraft("thaumcraft:ingot_thaumium"), "Fixture accidentally credited a metal craft");
        player.getInventory().setItem(9, ItemStack.EMPTY); before = state.save();
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        h.assertTrue(before.equals(state.save()) && xp == player.totalExperience, "Removing obsolete craft also removed the valid plate requirement");
        player.getInventory().setItem(9, plates);
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 1), ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("ESSENTIASMELTERTHAUMIUM") == 3 && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 3
                && player.getInventory().getItem(9).getCount() == 1 && player.getInventory().getItem(9).getTag().getString("kept").equals("extra-root-tag")
                && player.totalExperience == xp + 5, "Plate count,32 raw theory, XP or final empty-stage skip changed");
        before = state.save(); xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 1), ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(state.save()) && xp == player.totalExperience && PlayerKnowledge.load(before).isResearchCompleteStrict("ESSENTIASMELTERTHAUMIUM"), "Replay/save changed completed smelter research");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void actualThaumiumSmelterWorkbenchUsesStartedGateTwoCrystalsAnd250Vis(GameTestHelper h) {
        var player = player(h); var state = KnowledgeStore.get(player);
        h.setBlock(CENTER, ArcaneModule.WORKBENCH.get());
        var bench = (ArcaneWorkbenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CENTER));
        for (int slot : new int[]{0, 2}) bench.setItem(slot, item("plate_brass"));
        for (int slot : new int[]{3, 5, 6, 7, 8}) bench.setItem(slot, item("plate_thaumium"));
        bench.setItem(1, new ItemStack(CatalogBlocks.block("smelter_basic"))); bench.setItem(4, new ItemStack(CatalogBlocks.block("metal_alchemical")));
        var crystals = CatalogModule.aspectStack("crystal_essence", Aspect.FIRE, 1); crystals.setCount(2); bench.setItem(10, crystals);
        AuraManager.drainVis(h.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false); AuraManager.addVis(h.getLevel(), bench.getBlockPos(), 249);
        var before = bench.saveWithoutMetadata();
        h.assertTrue(bench.findRecipe(player) == null && bench.craft(player).isEmpty() && before.equals(bench.saveWithoutMetadata()), "Absent research paid the smelter recipe");
        complete(state, "TUBES"); state.setResearchStage("METALLURGY", 3);
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERTHAUMIUM", 0), ResearchProgression.Result.STARTED);
        var recipe = bench.findRecipe(player);
        h.assertTrue(recipe != null && recipe.getId().equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "arcane/essentiasmelterthaumium"))
                && recipe.vis() == 250 && recipe.crystalCost(1) == 2 && state.researchStage("ESSENTIASMELTERTHAUMIUM") == 1, "Started bare gate or original price changed");
        h.assertTrue(bench.craft(player).isEmpty() && before.equals(bench.saveWithoutMetadata()) && AuraManager.getVis(h.getLevel(), bench.getBlockPos()) == 249, "249 vis failed atomically");
        AuraManager.addVis(h.getLevel(), bench.getBlockPos(), 3);
        var output = bench.craft(player);
        h.assertTrue(output.is(CatalogBlocks.block("smelter_thaumium").asItem()) && output.getCount() == 1 && AuraManager.getVis(h.getLevel(), bench.getBlockPos()) == 2
                && bench.isEmpty() && state.hasCraft("thaumcraft:smelter_thaumium") && state.researchStage("ESSENTIASMELTERTHAUMIUM") == 1, "Craft consumed wrong ingredients/payment or completed research");
        h.setBlock(CENTER.above(), CatalogBlocks.block("smelter_thaumium"));
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(CENTER.above())) instanceof SmelterBlockEntity, "Recipe returned a visual-only machine");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void thaumatoriumRequiresBothCompletedParentsAndPaysKnowledgeOnlyThroughTheBook(GameTestHelper h) {
        var player = player(h); var state = KnowledgeStore.get(player);
        h.assertTrue(ResearchCatalog.get("THAUMATORIUM").parents().equals(List.of("CENTRIFUGE", "ESSENTIASMELTERTHAUMIUM")), "Thaumatorium parent chain changed");
        complete(state, "CENTRIFUGE"); state.setResearchStage("ESSENTIASMELTERTHAUMIUM", 2);
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 0), ResearchProgression.Result.LOCKED);
        complete(state, "ESSENTIASMELTERTHAUMIUM");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 0), ResearchProgression.Result.NO_BOOK);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 31); KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ARTIFICE", 18);
        var before = state.save(); int xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        h.assertTrue(before.equals(state.save()) && xp == player.totalExperience, "Mixed-category shortage partially paid");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "ALCHEMY", 4);
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 1), ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("THAUMATORIUM") == 3 && state.rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 3
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == 2 && xp + 5 == player.totalExperience
                && state.permanentWarp() == 0 && state.normalWarp() == 0, "Thaumatorium changed32+16 payment, XP, warp or conclusion skip");
        before = state.save(); xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "THAUMATORIUM", 1), ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(state.save()) && xp == player.totalExperience && PlayerKnowledge.load(before).isResearchCompleteStrict("THAUMATORIUM"), "Thaumatorium replay/save failed");
        result(h, ResearchNetwork.processAdvance(player, "ESSENTIASMELTERVOID", 0), ResearchProgression.Result.UNSUPPORTED);
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void stabilityRequiresActualDiscoveryAndAtomicallyConsumesRedstoneTenVitiumAndKnowledge(GameTestHelper h) {
        var player = player(h); var state = KnowledgeStore.get(player);
        h.assertTrue(ResearchCatalog.get("INFUSIONSTABLE").parents().equals(List.of("INFUSION", "METALLURGY@3", "!INSTABILITY")), "Changed hidden stability parents");
        complete(state, "INFUSION"); state.setResearchStage("METALLURGY", 3);
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 0), ResearchProgression.Result.LOCKED);
        // This payment test installs the discovery fact; a separate matrix test witnesses a real instability event.
        KnowledgeStore.recordFact(player, "!INSTABILITY");
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "INFUSION", 19);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 35);
        player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 2));
        var wrong = CatalogModule.aspectStack("phial_filled", Aspect.FLUX, 9); player.getInventory().setItem(10, wrong);
        var before = state.save(); var redstone = player.getInventory().getItem(9).copy(); int xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        h.assertTrue(before.equals(state.save()) && ItemStack.matches(redstone, player.getInventory().getItem(9)) && player.totalExperience == xp, "Nine-unit phial partially paid stability");
        player.getInventory().setItem(10, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, CatalogModule.aspectStack("phial_filled", Aspect.FLUX, 10));
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        player.getInventory().setItem(10, player.getOffhandItem().copy());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get())); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 1), ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("INFUSIONSTABLE") == 3 && state.rawKnowledge(KnowledgeType.OBSERVATION, "INFUSION") == 3
                && state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 3 && player.getInventory().getItem(9).getCount() == 1
                && player.getInventory().getItem(10).isEmpty() && xp + 5 == player.totalExperience, "Stability payment/conclusion changed or returned an invented empty phial");
        before = state.save(); xp = player.totalExperience;
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 1), ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(state.save()) && player.totalExperience == xp && PlayerKnowledge.load(before).isResearchCompleteStrict("INFUSIONSTABLE"), "Stability replay/reload changed payment");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void paidInlayThenStabilizerRecipesKeepOriginalStartedGateCountsAndCosts(GameTestHelper h) {
        var player = player(h); var state = KnowledgeStore.get(player);
        h.setBlock(CENTER, ArcaneModule.WORKBENCH.get());
        var bench = (ArcaneWorkbenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CENTER));
        bench.setItem(2, new ItemStack(Items.REDSTONE)); bench.setItem(6, new ItemStack(Items.GOLD_INGOT));
        var water = CatalogModule.aspectStack("crystal_essence", Aspect.WATER, 1); water.setCount(2); bench.setItem(11, water);
        AuraManager.drainVis(h.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false); AuraManager.addVis(h.getLevel(), bench.getBlockPos(), 300);
        h.assertTrue(bench.findRecipe(player) == null && bench.craft(player).isEmpty(), "Locked inlay recipe opened");
        complete(state, "INFUSION"); state.setResearchStage("METALLURGY", 3); KnowledgeStore.recordFact(player, "!INSTABILITY");
        result(h, ResearchNetwork.processAdvance(player, "INFUSIONSTABLE", 0), ResearchProgression.Result.STARTED);
        var inlay = bench.craft(player);
        h.assertTrue(inlay.is(CatalogBlocks.block("inlay").asItem()) && inlay.getCount() == 2 && bench.getItem(11).getCount() == 1
                && AuraManager.getVis(h.getLevel(), bench.getBlockPos()) == 275 && state.hasCraft("thaumcraft:inlay"), "Shapeless inlay count/25vis/water payment changed");
        for (int slot : new int[]{0, 2}) bench.setItem(slot, new ItemStack(CatalogBlocks.block("slab_arcane_stone")));
        for (int slot : new int[]{3, 5}) bench.setItem(slot, new ItemStack(CatalogBlocks.block("stone_arcane")));
        bench.setItem(1, new ItemStack(Items.REDSTONE_BLOCK)); bench.setItem(4, item("vis_resonator"));
        bench.setItem(7, item("mechanism_complex")); bench.setItem(6, inlay.split(1)); bench.setItem(8, inlay.split(1));
        bench.setItem(12, CatalogModule.aspectStack("crystal_essence", Aspect.EARTH, 1));
        bench.setItem(14, CatalogModule.aspectStack("crystal_essence", Aspect.ENTROPY, 1));
        var recipe = bench.findRecipe(player);
        h.assertTrue(recipe != null && recipe.getId().getPath().equals("infusion_components/stabilizer") && recipe.vis() == 250, "Stabilizer recipe or original cost changed");
        var output = bench.craft(player);
        h.assertTrue(output.is(CatalogBlocks.block("stabilizer").asItem()) && output.getCount() == 1 && bench.isEmpty() && inlay.isEmpty()
                && AuraManager.getVis(h.getLevel(), bench.getBlockPos()) == 25 && state.hasCraft("thaumcraft:stabilizer") && state.researchStage("INFUSIONSTABLE") == 1,
                "Stabilizer did not debit nine ingredients and Earth/Water/Entropy crystals or silently completed research");
        h.succeed();
    }
}
