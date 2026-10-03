package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.alchemy.CrucibleRecipes;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.infusion.InfusionModule;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class AuromancyProgressionGameTests {
    @GameTest(template = "empty")
    public static void blankFocusRequiresExactOrderCrystalAndReleasedAspectCost(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        var cost = focusCost(); var order = AspectCrystalItem.create(Aspect.ORDER);
        helper.assertTrue(CrucibleRecipes.find(order, cost, player) == null, "Focus bypassed completed auromancy prerequisite");
        complete(state, "UNLOCKAUROMANCY");
        var recipe = CrucibleRecipes.find(order, cost, player);
        helper.assertTrue(recipe != null && recipe.id().getPath().equals("focus_1")
                && recipe.output().is(item("focus_1").getItem()) && recipe.cost().visSize() == 35,
                "BETA26 focus recipe, order catalyst or total35 cost changed");
        var partial = focusCost(); partial.remove(Aspect.AURA, 1);
        helper.assertTrue(!recipe.matches(order, partial, player), "One missing Auram accepted");
        helper.assertTrue(!recipe.matchesCatalyst(AspectCrystalItem.create(Aspect.FIRE))
                && !recipe.matchesCatalyst(item("crystal_essence")), "Uninitialized/wrong aspect crystal impersonated Ordo");
        var mixed = order.copy(); new AspectList().add(Aspect.ORDER, 1).add(Aspect.FIRE, 1).writeToNBT(mixed.getOrCreateTag());
        var doubled = order.copy(); new AspectList().add(Aspect.ORDER, 2).writeToNBT(doubled.getOrCreateTag());
        helper.assertTrue(!recipe.matchesCatalyst(mixed) && !recipe.matchesCatalyst(doubled), "NBT list was treated as a partial match");
        order.getOrCreateTag().putString("other_mod", "retained");
        helper.assertTrue(recipe.matchesCatalyst(order), "Original IngredientNBTTC ignored extra unrelated root tags");
        var detached = recipe.catalystNbt(); detached.remove("Aspects");
        helper.assertTrue(!recipe.matchesCatalyst(mixed), "Caller mutated registered catalyst requirement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleCraftsFocusAndOwnershipDoesNotCreateStageProof(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "UNLOCKAUROMANCY");
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 0), ResearchProgression.Result.STARTED);
        player.getInventory().setItem(9, item("focus_1"));
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(pos);
        var prepared = new CompoundTag(); prepared.putInt("Heat", 200); prepared.putInt("Water", 1000);
        focusCost().writeToNBT(prepared); crucible.load(prepared);
        var catalyst = AspectCrystalItem.create(Aspect.ORDER, 2);
        helper.assertTrue(crucible.consume(catalyst, player) && catalyst.getCount() == 1 && crucible.water() == 950
                && crucible.aspects().visSize() == 0 && state.hasCraft("thaumcraft:focus_1"),
                "Committed focus craft failed to debit35 essentia, one crystal,50 water, or record evidence");
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.researchStage("BASEAUROMANCY") == 2 && !state.isResearchCompleteStrict("BASEAUROMANCY"),
                "Creating focus prematurely completed basic auromancy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireDamageFactIsStageGatedAndOrdinaryBurningDoesNotCompleteResearch(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "UNLOCKAUROMANCY");
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().inFire(), 1));
        helper.assertTrue(!state.isResearchCompleteStrict("f_onfire"), "Fire before basic lesson granted its fact");
        state.setResearchStage("BASEAUROMANCY", 1);
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().inFire(), 1));
        helper.assertTrue(!state.isResearchCompleteStrict("f_onfire"), "Fire at basic stage1 granted stage2 evidence");
        state.setResearchStage("BASEAUROMANCY", 2); player.setSecondsOnFire(3);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().generic(), 1));
        helper.assertTrue(!state.isResearchCompleteStrict("f_onfire"), "Fire ticks or non-fire damage manufactured proof");
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().inFire(), 1));
        helper.assertTrue(state.isResearchCompleteStrict("f_onfire") && !state.isResearchCompleteStrict("BASEAUROMANCY"),
                "Observed damage failed to grant fact or completed missing manipulator craft");
        var saved = state.save();
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().inFire(), 1));
        helper.assertTrue(saved.equals(state.save()) && PlayerKnowledge.load(saved).isResearchCompleteStrict("f_onfire"),
                "Repeat fire changed state or damage evidence did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void committedBasicAuromancyOpensRechargeBootsElementalAndFortressChain(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "UNLOCKAUROMANCY");
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 0), ResearchProgression.Result.STARTED);
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        for (int slot : new int[]{0, 2}) bench.setItem(slot, item("plate_iron"));
        bench.setItem(1, item("slab_arcane_stone"));
        for (int slot : new int[]{3, 5}) bench.setItem(slot, item("stone_arcane"));
        bench.setItem(4, item("vis_resonator"));
        for (int slot : new int[]{6, 8}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT));
        bench.setItem(7, item("table_stone"));
        for (int i = 0; i < ArcaneModule.PRIMALS.length; i++)
            bench.setItem(9 + i, AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i])));
        AuraManager.drainVis(helper.getLevel(), pos, Float.MAX_VALUE, false); AuraManager.addVis(helper.getLevel(), pos, 120);
        helper.assertTrue(bench.craft(player).isEmpty() && bench.getItem(0).getCount() == 1 && AuraManager.getVis(helper.getLevel(), pos) == 120,
                "Manipulator recipe bypassed stage2 or consumed a locked preview");
        // This new chain uses committed device actions, without setting BASEAUROMANCY stages or craft facts.
        var cruciblePos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlockAndUpdate(cruciblePos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);
        var prepared = new CompoundTag(); prepared.putInt("Heat", 200); prepared.putInt("Water", 1000);
        focusCost().writeToNBT(prepared); crucible.load(prepared);
        var catalyst = AspectCrystalItem.create(Aspect.ORDER);
        helper.assertTrue(crucible.consume(catalyst, player) && catalyst.isEmpty()
                && crucible.aspects().visSize() == 0 && crucible.water() == 950 && state.hasCraft("thaumcraft:focus_1"),
                "Basic chain did not commit the real focus craft");
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 1), ResearchProgression.Result.ADVANCED);
        var recipe = bench.findRecipe(player);
        helper.assertTrue(recipe != null && recipe.research().equals("BASEAUROMANCY@2") && recipe.vis() == 100,
                "Manipulator requires completed lesson and deadlocks its own craft proof");
        var output = bench.craft(player);
        helper.assertTrue(output.is(item("wand_workbench").getItem()) && state.hasCraft("thaumcraft:wand_workbench")
                && AuraManager.getVis(helper.getLevel(), pos) == 20, "Real arcane recipe did not pay100 or record manipulator proof");
        for (int i = 0; i < ArcaneModule.PRIMALS.length; i++) {
            boolean charged = Set.of("terra", "aqua").contains(ArcaneModule.PRIMALS[i]);
            helper.assertTrue(bench.getItem(9 + i).getCount() == (charged ? 0 : 1), "Wrong crystal debit: " + ArcaneModule.PRIMALS[i]);
        }
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        AuromancyProgressionEvents.hurt(new LivingHurtEvent(player, player.damageSources().inFire(), 1));
        result(helper, ResearchNetwork.processAdvance(player, "BASEAUROMANCY", 2), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("BASEAUROMANCY") == 4 && PlayerKnowledge.load(state.save()).hasCraft("thaumcraft:wand_workbench"),
                "Empty conclusion or committed manipulator evidence did not persist");
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", 34);
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 2,
                "Naturally completed basic chain did not open the original recharge payment");

        bench.clearContent();
        bench.setItem(1, item("vis_resonator"));
        for (int slot : new int[]{3, 5}) bench.setItem(slot, new ItemStack(Items.DIAMOND));
        bench.setItem(4, new ItemStack(Items.GOLD_INGOT));
        for (int slot : new int[]{6, 7, 8}) bench.setItem(slot, new ItemStack(Items.STONE));
        for (int i = 0; i < ArcaneModule.PRIMALS.length; i++)
            bench.setItem(9 + i, AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i])));
        AuraManager.addVis(helper.getLevel(), pos, 80);
        var recharge = bench.craft(player);
        helper.assertTrue(recharge.is(item("recharge_pedestal").getItem()) && AuraManager.getVis(helper.getLevel(), pos) == 0
                && state.hasCraft("thaumcraft:recharge_pedestal"), "Original recharge recipe failed committed100-vis crafting");
        for (int i = 0; i < ArcaneModule.PRIMALS.length; i++)
            helper.assertTrue(bench.getItem(9 + i).getCount() == (Set.of("aer", "ordo").contains(ArcaneModule.PRIMALS[i]) ? 0 : 1),
                    "Recharge did not consume exactly Air1/Order1");

        // INFUSION and METALLURGY are pre-existing upstream fixtures. None of the five new entries is granted.
        complete(state, "INFUSION"); state.setResearchStage("METALLURGY", 3);
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 100);
        var stats = player.getStats();
        stats.setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 160001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.SPRINT_ONE_CM), 80001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.SWIM_ONE_CM), 8001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.JUMP), 501);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        KnowledgeStore.recordScan(player, "item:minecraft:oak_boat", AspectRegistry.getAspects(new ItemStack(Items.OAK_BOAT)));
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 1), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 0), ResearchProgression.Result.STARTED);
        for (String name : new String[]{"axe", "sword", "pick", "shovel", "hoe"}) takeVanillaCraft(helper, player, "thaumium_" + name);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "BASICS", 32);
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 1), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.recordScan(player, "item:minecraft:shield", AspectRegistry.getAspects(new ItemStack(Items.SHIELD)));
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 1), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(state.save());
        for (String key : new String[]{"BASEAUROMANCY", "RECHARGEPEDESTAL", "BOOTSTRAVELLER", "ELEMENTALTOOLS", "ARMORFORTRESS"})
            helper.assertTrue(loaded.isResearchCompleteStrict(key), "Committed new research chain failed save/reload: " + key);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 4
                && state.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 0
                && !loaded.isResearchCompleteStrict("FORTRESSMASK"), "New chain changed payments or completed unsupported descendant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rechargeRequiresCompletedBaseAndPaysThirtyTwoRawTheory(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); state.setResearchStage("BASEAUROMANCY", 2);
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 0), ResearchProgression.Result.LOCKED);
        complete(state, "BASEAUROMANCY");
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", 31);
        var saved = state.save(); int experience = player.totalExperience;
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(saved.equals(state.save()) && experience == player.totalExperience, "Failed recharge payment mutated state");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", 4);
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 3 && state.researchStage("RECHARGEPEDESTAL") == 3,
                "Recharge payment/conclusion skipped original cost");
        result(helper, ResearchNetwork.processAdvance(player, "RECHARGEPEDESTAL", 1), ResearchProgression.Result.STALE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void travellerNeedsBothParentsActualStatisticsAndDiscoveredMotus(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "INFUSION");
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 0), ResearchProgression.Result.LOCKED);
        complete(state, "RECHARGEPEDESTAL");
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 35);
        var stats = player.getStats();
        stats.setValue(player, Stats.CUSTOM.get(Stats.WALK_ONE_CM), 160001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.SPRINT_ONE_CM), 80001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.SWIM_ONE_CM), 8001);
        stats.setValue(player, Stats.CUSTOM.get(Stats.JUMP), 500);
        InfusionProgressionEvents.checkPeriodicFacts(player);
        // Real scan data, rather than granting !motus through a QA research shortcut.
        var boat = new ItemStack(Items.OAK_BOAT);
        helper.assertTrue(AspectRegistry.getAspects(boat).getAmount(Aspect.MOTION) > 0, "Fixture needs original boat Motus");
        KnowledgeStore.recordScan(player, "item:minecraft:oak_boat", AspectRegistry.getAspects(boat));
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 35, "Missing strict jump proof partially paid boots");
        stats.setValue(player, Stats.CUSTOM.get(Stats.JUMP), 501); InfusionProgressionEvents.checkPeriodicFacts(player);
        result(helper, ResearchNetwork.processAdvance(player, "BOOTSTRAVELLER", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 3 && state.researchStage("BOOTSTRAVELLER") == 3,
                "Boot lesson altered costs/conclusion or failed genuine facts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fortressKeepsMetallurgyStageThreeAndRequiresProtectScan(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "BOOTSTRAVELLER");
        state.setResearchStage("METALLURGY", 2);
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 3);
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 33);
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        var shield = new ItemStack(Items.SHIELD);
        helper.assertTrue(AspectRegistry.getAspects(shield).getAmount(Aspect.PROTECT) > 0, "Shield lacks original Praemunio scan");
        KnowledgeStore.recordScan(player, "item:minecraft:shield", AspectRegistry.getAspects(shield));
        result(helper, ResearchNetwork.processAdvance(player, "ARMORFORTRESS", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 1 && state.isResearchCompleteStrict("!praemunio"),
                "Fortress lost its actual Protect prerequisite or charged wrong theory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newCanonicalChainOpensOnlyNineOfFiftySixRegisteredInfusions(GameTestHelper helper) {
        var state = KnowledgeStore.get(player(helper));
        for (ResearchEntry entry : ResearchCatalog.entries()) if (ResearchProgression.isImplemented(entry.key())) complete(state, entry.key());
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(InfusionModule.RECIPE_TYPE.get());
        Set<String> open = recipes.stream().filter(recipe -> recipe.unlocked(state)).map(recipe -> recipe.getId().getPath()).collect(Collectors.toSet());
        helper.assertTrue(recipes.size() == 56 && open.equals(Set.of("infusion/bootstraveller", "infusion/thaumiumfortresshelm",
                "infusion/thaumiumfortresschest", "infusion/thaumiumfortresslegs", "infusion/elementalaxe",
                "infusion/elementalsword", "infusion/elementalpick", "infusion/elementalshovel", "infusion/elementalhoe")),
                "Unavailable mask/late research was bypassed: " + open);
        for (String key : new String[]{"FORTRESSMASK", "INFUSIONENCHANTMENT", "RUNICSHIELDING", "FOCUSADVANCED"})
            helper.assertTrue(!ResearchProgression.isImplemented(key), "Incomplete branch was silently advertised: " + key);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void elementalToolsKeepStageThreeAndPayOnlyAfterFiveRealVanillaCrafts(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "BOOTSTRAVELLER");
        state.setResearchStage("METALLURGY", 2);
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 3);
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "INFUSION", 35);
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "BASICS", 31);
        for (String name : new String[]{"axe", "sword", "pick", "shovel", "hoe"}) {
            String id = "thaumium_" + name;
            player.getInventory().setItem(9, item(id));
            helper.assertTrue(!state.hasCraft("thaumcraft:" + id), "Owning a thaumium tool created craft proof");
            takeVanillaCraft(helper, player, id);
            helper.assertTrue(state.hasCraft("thaumcraft:" + id), "Taking actual result slot lost craft proof: " + id);
            result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
            helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 35
                    && state.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 31, "Incomplete multi-category lesson partly paid");
        }
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "BASICS", 4);
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("ELEMENTALTOOLS") == 3
                && state.rawKnowledge(KnowledgeType.THEORY, "INFUSION") == 3
                && state.rawKnowledge(KnowledgeType.THEORY, "BASICS") == 3,
                "Five crafted tools did not pay the original32+32 requirements and conclusion");
        result(helper, ResearchNetwork.processAdvance(player, "ELEMENTALTOOLS", 1), ResearchProgression.Result.STALE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void initialRootTouchFireRequiresCompletedBasicLesson(GameTestHelper helper) {
        var state = KnowledgeStore.get(player(helper)); var focus = item("focus_1"); var graph = FocusGraph.touchFire(1, 0);
        for (String key : new String[]{FocusNodeRegistry.ROOT, FocusNodeRegistry.TOUCH, FocusNodeRegistry.FIRE})
            helper.assertTrue(FocusNodeRegistry.get(key).research().equals("BASEAUROMANCY"), "Initial node research differs from BETA26: " + key);
        state.setResearchStage("BASEAUROMANCY", 2);
        var locked = FocusCompiler.compile(graph, focus, state::isResearchCompleteStrict);
        helper.assertTrue(!locked.success() && locked.error().equals("missing_research"),
                "Initial Fire focus can manufacture fire evidence before its own lesson completes");
        complete(state, "BASEAUROMANCY"); var compiled = FocusCompiler.compile(graph, focus, state::isResearchCompleteStrict);
        helper.assertTrue(compiled.success() && compiled.plan().complexity() == 4,
                "Completed base lesson did not unlock original Touch2+Fire2 minimum graph");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "auromancy_progress"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get())); return player;
    }
    private static ItemStack item(String id) { return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id))); }
    private static void takeVanillaCraft(GameTestHelper helper, ServerPlayer player, String id) {
        var recipe = (ShapedRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)).orElseThrow();
        var menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player ignored, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player ignored) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int y = 0; y < recipe.getHeight(); y++) for (int x = 0; x < recipe.getWidth(); x++) {
            var ingredient = recipe.getIngredients().get(x + y * recipe.getWidth());
            if (!ingredient.isEmpty()) grid.setItem(x + y * 3, ingredient.getItems()[0].copyWithCount(1));
        }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Actual registered thaumium recipe did not match: " + id);
        var result = new SimpleContainer(recipe.assemble(grid, helper.getLevel().registryAccess()));
        helper.assertTrue(!KnowledgeStore.get(player).hasCraft("thaumcraft:" + id), "Recipe preview awarded craft proof");
        var slot = new ResultSlot(player, grid, result, 0, 0, 0);
        ItemStack taken = slot.remove(1); slot.onTake(player, taken);
        helper.assertTrue(taken.is(item(id).getItem()) && grid.isEmpty() && result.isEmpty(),
                "Vanilla result-slot commit did not consume inputs: " + id);
    }
    private static void complete(PlayerKnowledge knowledge, String key) { knowledge.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1); }
    private static AspectList focusCost() { return new AspectList().add(Aspect.CRYSTAL, 20).add(Aspect.MAGIC, 10).add(Aspect.AURA, 5); }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
}
