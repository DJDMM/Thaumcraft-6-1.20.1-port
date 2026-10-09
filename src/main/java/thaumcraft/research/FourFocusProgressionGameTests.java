package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Original JSON gates, actual scan commits and physical research payments, independent of spell execution. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class FourFocusProgressionGameTests {
    @GameTest(template = "empty")
    public static void fourCanonicalBranchesUseOriginalStrictParentsAndKeepLateResearchClosed(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        helper.assertTrue(ResearchCatalog.get("FOCUSBOLT").parents().equals(List.of("FOCUSPROJECTILE@2"))
                        && ResearchCatalog.get("FOCUSFLUX").parents().equals(List.of("FOCUSELEMENTAL"))
                        && ResearchCatalog.get("FOCUSHEAL").parents().equals(List.of("FOCUSFLUX"))
                        && ResearchCatalog.get("FOCUSBREAK").parents().equals(List.of("FOCUSFLUX")),
                "Four original parents changed");
        state.setResearchStage("FOCUSPROJECTILE", 1); state.setResearchStage("FOCUSELEMENTAL", 2);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBOLT", 0), ResearchProgression.Result.LOCKED);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("FOCUSPROJECTILE", 2); complete(state, "FOCUSELEMENTAL");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBOLT", 0), ResearchProgression.Result.STARTED);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(!state.isResearchCompleteStrict("FOCUSPROJECTILE"), "Bolt demanded or granted Projectile completion beyond its @2 parent");
        for (String key : new String[]{"FOCUSHEAL", "FOCUSBREAK"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.LOCKED);
        complete(state, "FOCUSFLUX");
        for (String key : new String[]{"FOCUSHEAL", "FOCUSBREAK"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(ResearchCatalog.entries().stream().filter(entry -> ResearchProgression.isImplemented(entry.key())).count() ==82,
                "Canonical inventory must include completed clockwork mind progression");
        for (String key : new String[]{"FORTRESSMASK", "INFUSIONENCHANTMENT", "RUNICSHIELDING"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.UNSUPPORTED);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBOLT", 1), ResearchProgression.Result.NO_BOOK);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaAspectScansAreActualCommitsAndHoverNeverGrantsFourFocusResearch(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        var items = new net.minecraft.world.item.Item[]{Items.REDSTONE, Items.NETHER_WART, Items.WHEAT_SEEDS, Items.COBBLESTONE};
        var facts = new String[]{"!potentia", "!vitium", "!victus", "!perditio"};
        for (int index = 0; index < items.length; index++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(items[index])); player.setShiftKeyDown(true);
            var before = state.save(); ScanningNetwork.capture(player);
            helper.assertTrue(before.equals(state.save()), "Aspect hover awarded a four-focus proof");
            scanHeld(player, new ItemStack(items[index]));
            helper.assertTrue(state.isResearchCompleteStrict(facts[index]), "Actual vanilla scan omitted " + facts[index]);
        }
        for (String key : new String[]{"FOCUSBOLT", "FOCUSFLUX", "FOCUSHEAL", "FOCUSBREAK"})
            helper.assertTrue(state.researchStage(key) == 0, "Aspect scan granted a canonical lesson directly");
        helper.assertTrue(PlayerKnowledge.load(state.save()).isResearchCompleteStrict("!vitium"), "Actual Vitium discovery did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourBranchesPayExactKnowledgeAndPhysicalEnchantmentsThroughServerProgression(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        state.setResearchStage("FOCUSPROJECTILE", 2); complete(state, "FOCUSELEMENTAL");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBOLT", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.REDSTONE));
        knowledge(player, 16, 31);
        assertMissingUnchanged(helper, player, "FOCUSBOLT");
        knowledge(player, 15, 32);
        assertMissingUnchanged(helper, player, "FOCUSBOLT");
        knowledge(player, 19, 35);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBOLT", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("FOCUSBOLT") == 3 && state.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 3
                        && state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 3,
                "Bolt did not pay16 observation/32 theory and skip only its empty conclusion");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.NETHER_WART)); knowledge(player, 3, 35);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 1), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSHEAL", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.WHEAT_SEEDS)); knowledge(player, 3, 35);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSHEAL", 1), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.COBBLESTONE)); knowledge(player, 3, 35);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE); silk.enchant(Enchantments.SILK_TOUCH, 1);
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE); fortune.enchant(Enchantments.BLOCK_FORTUNE, 2);
        player.getInventory().setItem(1, silk); player.getInventory().setItem(2, fortune);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 1), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(loaded.researchStage("FOCUSBOLT") == 3 && loaded.researchStage("FOCUSFLUX") == 2
                        && loaded.researchStage("FOCUSHEAL") == 2 && loaded.researchStage("FOCUSBREAK") == 3
                        && loaded.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 3
                        && loaded.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 3
                        && player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(2).isEmpty(),
                "Original stage conclusions,32-raw payments, physical costs or save/reload changed");
        helper.assertTrue(!loaded.isResearchCompleteStrict("FOCUSPROJECTILE") && !loaded.isResearchCompleteStrict("FOCUSADVANCED"),
                "Completing four lessons implicitly granted unrelated progression");
        var before = state.save(); int xp = player.totalExperience;
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 1), ResearchProgression.Result.STALE);
        helper.assertTrue(before.equals(state.save()) && xp == player.totalExperience, "Replayed Break completion paid again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakConsumesMainInventoryEnchantedToolAndStoredBookIgnoringNameWearAndHigherLevels(GameTestHelper helper) {
        var player = player(helper); prepareBreak(player); var state = KnowledgeStore.get(player);
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE); fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        fortune.enchant(Enchantments.UNBREAKING, 2); fortune.setDamageValue(147); fortune.setHoverName(Component.literal("Paid tool"));
        ItemStack silkBook = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(silkBook, new EnchantmentInstance(Enchantments.SILK_TOUCH, 1));
        player.getInventory().setItem(1, fortune); player.setItemInHand(InteractionHand.OFF_HAND, silkBook);
        assertMissingUnchanged(helper, player, "FOCUSBREAK");
        helper.assertTrue(!fortune.isEmpty() && player.getOffhandItem().is(Items.ENCHANTED_BOOK), "Missing main-inventory cost consumed offhand or the first tool");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); player.getInventory().setItem(2, silkBook);
        var rows = ResearchBookRequirements.rows(ResearchCatalog.get("FOCUSBREAK").stages().get(0), state, player.getInventory());
        helper.assertTrue(rows.stream().filter(row -> row.kind() == ResearchBookRequirements.Kind.ITEM).count() == 2
                        && rows.stream().filter(row -> row.kind() == ResearchBookRequirements.Kind.ITEM).allMatch(ResearchBookRequirements.Row::met),
                "Book preview disagrees with the physical enchantment payment");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(2).isEmpty()
                        && state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 3,
                "Placeholder requirements merely checked enchantments instead of consuming both items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSingleDoubleEnchantedItemCannotPayTwoCostsAndMissingPaymentIsAtomic(GameTestHelper helper) {
        var player = player(helper); prepareBreak(player);
        ItemStack both = new ItemStack(Items.DIAMOND_PICKAXE);
        both.enchant(Enchantments.SILK_TOUCH, 1); both.enchant(Enchantments.BLOCK_FORTUNE, 1);
        player.getInventory().setItem(1, both);
        assertMissingUnchanged(helper, player, "FOCUSBREAK");
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 1, "One double-enchanted item partly paid two physical costs");
        var rows = ResearchBookRequirements.rows(ResearchCatalog.get("FOCUSBREAK").stages().get(0), KnowledgeStore.get(player), player.getInventory());
        helper.assertTrue(rows.stream().filter(row -> row.kind() == ResearchBookRequirements.Kind.ITEM && row.met()).count() == 1,
                "Book preview forgot shared main-inventory reservations");
        player.getInventory().setItem(2, both.copy());
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(2).isEmpty(),
                "Distinct double-enchanted items failed both original requirements");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingAspectFactAndOrdinaryEnchantmentsNeverPartlyPayKnowledge(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); complete(state, "FOCUSELEMENTAL");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 0), ResearchProgression.Result.STARTED);
        knowledge(player, 7, 35); assertMissingUnchanged(helper, player, "FOCUSFLUX");
        scanHeld(player, new ItemStack(Items.NETHER_WART)); knowledge(player, 7, 35);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSFLUX", 1), ResearchProgression.Result.COMPLETE);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSBREAK", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.COBBLESTONE)); knowledge(player, 7, 35);
        ItemStack wrong = new ItemStack(Items.DIAMOND_PICKAXE); wrong.enchant(Enchantments.UNBREAKING, 3);
        player.getInventory().setItem(1, wrong);
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        assertMissingUnchanged(helper, player, "FOCUSBREAK");
        helper.assertTrue(player.getInventory().getItem(1).is(Items.DIAMOND_PICKAXE) && player.getInventory().getItem(2).is(Items.DIAMOND_PICKAXE),
                "Wrong or unenchanted tools were consumed for a missing enchantment proof");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFourRuntimeDefinitionsRequireCompletedOwnResearchBeforeManufacturing(GameTestHelper helper) {
        var state = new PlayerKnowledge(); complete(state, "BASEAUROMANCY");
        var keys = new String[]{"FOCUSBOLT", "FOCUSFLUX", "FOCUSHEAL", "FOCUSBREAK"};
        var nodeKeys = new String[]{FocusNodeRegistry.BOLT, FocusNodeRegistry.FLUX, FocusNodeRegistry.HEAL, FocusNodeRegistry.BREAK};
        var focus = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "focus_1")));
        for (int index = 0; index < keys.length; index++) {
            state.setResearchStage(keys[index], 1);
            String medium = index == 0 ? nodeKeys[index] : FocusNodeRegistry.TOUCH;
            String effect = index == 0 ? FocusNodeRegistry.FIRE : nodeKeys[index];
            var graph = new FocusGraph(List.of(new FocusGraph.Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                    new FocusGraph.Node(1, 0, List.of(2), 0, 1, medium, Map.of()),
                    new FocusGraph.Node(2, 1, List.of(), 0, 2, effect, Map.of())));
            var started = FocusCompiler.compile(graph, focus, state::isResearchCompleteStrict);
            helper.assertTrue(!started.success() && started.error().equals("missing_research"), "Merely started lesson unlocked " + nodeKeys[index]);
            complete(state, keys[index]);
            helper.assertTrue(FocusCompiler.compile(graph, focus, state::isResearchCompleteStrict).success(), "Completed lesson did not unlock " + nodeKeys[index]);
        }
        helper.assertTrue(!state.isResearchCompleteStrict("FOCUSADVANCED") && !state.isResearchCompleteStrict("FOCUSGREATER"),
                "Basic-focus compiler granted higher focus crafting gates");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "four_focus_progress")) {
            @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
            @Override public void displayClientMessage(Component message, boolean actionBar) { /* Offline QA has no network connection. */ }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get())); return player;
    }
    private static void prepareBreak(ServerPlayer player) {
        complete(KnowledgeStore.get(player), "FOCUSFLUX");
        if (ResearchNetwork.processAdvance(player, "FOCUSBREAK", 0) != ResearchProgression.Result.STARTED)
            throw new IllegalStateException("Could not start Break fixture");
        scanHeld(player, new ItemStack(Items.COBBLESTONE)); knowledge(player, 7, 35);
    }
    private static void scanHeld(ServerPlayer player, ItemStack input) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, input); player.setShiftKeyDown(true);
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        ScanningModule.THAUMOMETER.get().use(player.level(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void complete(PlayerKnowledge state, String key) {
        state.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1);
    }
    private static void knowledge(ServerPlayer player, int observation, int theory) {
        var state = KnowledgeStore.get(player);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", observation - state.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY"));
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", theory - state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY"));
    }
    private static void assertMissingUnchanged(GameTestHelper helper, ServerPlayer player, String key) {
        var before = KnowledgeStore.get(player).save(); int xp = player.totalExperience;
        var inventoryBefore = new net.minecraft.nbt.ListTag(); player.getInventory().save(inventoryBefore);
        result(helper, ResearchNetwork.processAdvance(player, key, 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        var inventoryAfter = new net.minecraft.nbt.ListTag(); player.getInventory().save(inventoryAfter);
        helper.assertTrue(before.equals(KnowledgeStore.get(player).save()) && xp == player.totalExperience && inventoryBefore.equals(inventoryAfter),
                "Missing four-focus requirement partly spent knowledge, XP or inventory");
    }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
}
