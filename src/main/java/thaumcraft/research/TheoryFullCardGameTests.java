package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.theory.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real table transactions with seeded, saved BETA26 card offers; not a full survival playthrough. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryFullCardGameTests {
    private static final List<String> NEW = List.of("curio", "concentrate", "reactions", "synthesis", "calibrate",
            "mind_over_matter", "tinker", "measure", "channel", "infuse", "focus", "awareness", "spellbinding",
            "sculpting", "scripting", "synergy", "dark_whispers", "glyphs", "portal", "revelation", "realization");

    private static ServerPlayer player(GameTestHelper helper, boolean unlock) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "full_card_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (unlock) {
            PlayerKnowledge knowledge = KnowledgeStore.get(player);
            for (String category : ResearchCategories.keys()) {
                ResearchEntry entry = ResearchCatalog.get("UNLOCK" + category);
                if (entry != null) knowledge.setResearchStage(entry.key(), entry.stages().size() + 1);
            }
            knowledge.setResearchStage("CELESTIALSCANNING", 1);
        }
        return player;
    }

    private static TheorySession session(ServerPlayer player, TheoryCard card, int inspiration, Map<String, Integer> totals, long seed) {
        CompoundTag data = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), Set.of(), RandomSource.create(1)).save();
        data.putInt("InspirationStart", 5);
        data.putInt("Inspiration", inspiration);
        data.putLong("RandomSeed", seed);
        CompoundTag progress = new CompoundTag();
        totals.forEach(progress::putInt);
        data.put("Totals", progress);
        ListTag choices = new ListTag();
        if (card != null) choices.add(card.save());
        data.put("Choices", choices);
        return TheorySession.load(data);
    }

    private static TheoryCard card(String id, ServerPlayer player, long seed, boolean aid) {
        return TheoryCard.initialize(id, seed, aid,
                session(player, null, 5, Map.of("ALCHEMY", 10, "ARTIFICE", 20, "INFUSION", 30, "BASICS", 40), 1),
                KnowledgeStore.get(player), player.getInventory(), player.experienceLevel);
    }

    private static ResearchTableBlockEntity table(GameTestHelper helper, ServerPlayer player, TheoryCard card,
                                                   int inkDamage, int paper, int inspiration,
                                                   Map<String, Integer> totals, long seed) {
        BlockPos pos = player.blockPosition();
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.TABLE.get().defaultBlockState());
        ResearchTableBlockEntity table = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        ItemStack tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        tools.setDamageValue(inkDamage);
        table.setItem(0, tools);
        table.setItem(1, paper > 0 ? new ItemStack(Items.PAPER, paper) : ItemStack.EMPTY);
        CompoundTag saved = table.saveWithoutMetadata();
        saved.put("Session", session(player, card, inspiration, totals, seed).save());
        table.load(saved);
        return table;
    }

    private static void supplies(ServerPlayer player, TheoryCard card) {
        player.getInventory().clearContent();
        int slot = 0;
        for (var required : card.requiredItems()) player.getInventory().setItem(slot++, required.stack());
    }
    private static int amount(ServerPlayer player, ItemStack wanted) {
        return player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, wanted))
                .mapToInt(ItemStack::getCount).sum();
    }
    private static void accepted(GameTestHelper helper, ResearchTableBlockEntity table, ServerPlayer player) {
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.ACCEPTED, "Real table rejected initialized card");
    }

    @GameTest(template = "empty")
    public static void allRegisteredCardsInitializePersistParametersAndKeepOriginalGates(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        player.experienceLevel = 7;
        player.getInventory().setItem(4, CatalogModule.stack("curio_knowledge"));
        helper.assertTrue(TheoryCard.ids().size() == 33 && !TheoryCard.ids().contains("truth")
                && !TheoryCard.ids().contains("dragon_egg"), "Card registry differs from BETA26's 33 registrations");
        for (String id : TheoryCard.ids()) {
            TheoryCard card = card(id, player, 29, true);
            if (id.equals("analyze")) { helper.assertTrue(card == null, "Analyze binary bug was silently repaired"); continue; }
            helper.assertTrue(card != null && TheoryCard.load(card.save()) != null
                    && card.save().equals(TheoryCard.load(card.save()).save()), "Initialized parameters failed to reload: " + id);
        }
        player.getInventory().clearContent();
        helper.assertTrue(card("curio", player, 1, false) == null, "Curio drew without a main-inventory curio");
        player.getInventory().offhand.set(0, CatalogModule.stack("curio_arcane"));
        helper.assertTrue(card("curio", player, 1, false) == null, "Offhand curio admitted the card");
        player.experienceLevel = 0;
        helper.assertTrue(card("spellbinding", player, 1, false) == null, "Spellbinding initialized at zero XP");
        TheorySession empty = session(player, null, 5, Map.of(), 1);
        helper.assertTrue(TheoryCard.initialize("synergy", 1, false, empty, KnowledgeStore.get(player),
                player.getInventory(), 1) == null, "Synergy initialized below 15 combined progress");
        helper.assertTrue(card("dark_whispers", player, 1, true) != null, "Dark Whispers incorrectly needs XP to draw");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allTwentyOneCardsPayThroughSavedTableAndConsumeOnlyFlaggedItems(GameTestHelper helper) {
        for (String id : NEW) {
            ServerPlayer player = player(helper, true);
            player.experienceLevel = 7;
            player.getInventory().setItem(0, CatalogModule.stack("curio_knowledge"));
            TheoryCard card = card(id, player, 31, true);
            helper.assertTrue(card != null, "Missing new card " + id);
            supplies(player, card);
            ResearchTableBlockEntity table = table(helper, player, card, 0, 2, 5,
                    Map.of("ALCHEMY", 10, "ARTIFICE", 20, "INFUSION", 30), 5);
            CompoundTag saved = table.saveWithoutMetadata();
            table.load(saved);
            helper.assertTrue(saved.equals(table.saveWithoutMetadata()), "Reload changed initialized offer " + id);
            long revision = table.revision();
            accepted(helper, table, player);
            for (var item : card.requiredItems())
                helper.assertTrue(amount(player, item.stack()) == (item.consumed() ? 0 : item.stack().getCount()),
                        "Consumed/presence-only semantics changed for " + id);
            helper.assertTrue(table.getItem(0).getDamageValue() == (id.equals("scripting") ? 2 : 1)
                    && table.getItem(1).getCount() == (id.equals("scripting") ? 1 : 2), "Wrong table payment " + id);
            helper.assertTrue(player.experienceLevel == (id.equals("spellbinding") ? 2 : id.equals("dark_whispers") ? 0 : 7),
                    "Wrong XP cost " + id);
            helper.assertTrue(table.session().inspiration() == (id.equals("portal") ? 5 : 4),
                    "Inspiration refund/cost clamp order changed " + id);
            CompoundTag after = table.saveWithoutMetadata();
            ListTag inventory = player.getInventory().save(new ListTag());
            helper.assertTrue(table.select(player, revision, 0) == TheoryResult.STALE
                    && after.equals(table.saveWithoutMetadata()) && inventory.equals(player.getInventory().save(new ListTag())),
                    "Replayed offer paid/rewarded twice " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalsAndPhialsRequireExactAspectNBTAndBothPaymentsAreAtomic(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard card = card("infuse", player, 18, false);
        supplies(player, card);
        ItemStack wrong = CatalogModule.aspectStack("phial_filled", Aspect.AIR, 10);
        if (card.aspect("aspect1") == Aspect.AIR) wrong = CatalogModule.aspectStack("phial_filled", Aspect.FIRE, 10);
        player.getInventory().setItem(1, wrong);
        ResearchTableBlockEntity table = table(helper, player, card, 0, 3, 5, Map.of(), 4);
        CompoundTag before = table.saveWithoutMetadata();
        ListTag inventory = player.getInventory().save(new ListTag());
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.INVALID
                && before.equals(table.saveWithoutMetadata()) && inventory.equals(player.getInventory().save(new ListTag())),
                "Wrong phial aspect consumed the matching infusion object");
        ItemStack malformed = card.requiredItems().get(1).stack();
        malformed.getTag().getList("Aspects", 10).getCompound(0).putInt("amount", 9);
        player.getInventory().setItem(1, malformed);
        helper.assertTrue(!card.hasRequiredItems(player.getInventory()), "Nine essentia matched original ten-unit phial");
        player.getInventory().setItem(1, card.requiredItems().get(1).stack());
        accepted(helper, table, player);
        TheoryCard concentrate = card("concentrate", player, 2, false);
        supplies(player, concentrate);
        ItemStack crystal = concentrate.requiredItems().get(0).stack();
        crystal.setHoverName(Component.literal("Extra tag rejects original aspect template"));
        player.getInventory().setItem(0, crystal);
        helper.assertTrue(!concentrate.hasRequiredItems(player.getInventory()), "BETA26 found-to-template relaxed NBT was reversed");
        player.getInventory().setItem(0, AspectCrystalItem.create(Aspect.AIR));
        helper.assertTrue(!concentrate.hasRequiredItems(player.getInventory()), "Any crystal impersonated required compound aspect");
        player.getInventory().setItem(0, concentrate.requiredItems().get(0).stack());
        helper.assertTrue(concentrate.hasRequiredItems(player.getInventory()), "Exact compound crystal rejected");
        player.getInventory().offhand.set(0, player.getInventory().removeItemNoUpdate(0));
        helper.assertTrue(!concentrate.hasRequiredItems(player.getInventory()), "Offhand crystal paid a main-inventory requirement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void synthesisConsumesComponentsProducesCanonicalCrystalAndSurvivesReload(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard card = card("synthesis", player, 77, false);
        supplies(player, card);
        ResearchTableBlockEntity table = table(helper, player, card, 0, 2, 4, Map.of(), 4);
        CompoundTag saved = table.saveWithoutMetadata();
        table.load(saved);
        accepted(helper, table, player);
        helper.assertTrue(table.session().totals().get("ALCHEMY") == 40, "Synthesis did not grant exactly 40% Alchemy");
        helper.assertTrue(amount(player, AspectCrystalItem.create(card.aspect("aspect3"))) == 1,
                "Synthesis did not produce one correctly tagged compound crystal");
        for (var required : card.requiredItems()) helper.assertTrue(amount(player, required.stack()) == 0, "Synthesis retained a consumed component");
        helper.assertTrue(card.aspect("aspect3").getComponents()[0] == card.aspect("aspect1")
                && card.aspect("aspect3").getComponents()[1] == card.aspect("aspect2"), "Seed chose unrelated synthesis components");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void synergyUsesOriginalRoundRobinOrderAndExtendsRewardPenaltyBoundary(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard card = card("synergy", player, 4, false);
        ResearchTableBlockEntity table = table(helper, player, card, 0, 2, 5,
                Map.of("ARTIFICE", 2, "ALCHEMY", 8, "INFUSION", 9), 1);
        accepted(helper, table, player);
        helper.assertTrue(table.session().totals().equals(Map.of("ALCHEMY", 1, "INFUSION", 3, "GOLEMANCY", 30))
                && table.session().penaltyStart() == 1, "Synergy changed Artifice/Alchemy/Infusion subtraction order");
        table = table(helper, player, card, 0, 2, 5, Map.of("ALCHEMY", 14), 1);
        CompoundTag before = table.saveWithoutMetadata();
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.INVALID
                && before.equals(table.saveWithoutMetadata()), "Synergy paid below its activation threshold");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void xpCardsAndEldritchCardsKeepLevelAndSeparateWarpEffects(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        player.experienceLevel = 3;
        TheoryCard spell = card("spellbinding", player, 1, false);
        ResearchTableBlockEntity table = table(helper, player, spell, 0, 2, 5, Map.of(), 2);
        accepted(helper, table, player);
        helper.assertTrue(player.experienceLevel == 0 && table.session().totals().get("AUROMANCY") == 15,
                "Spellbinding did not use min(5, XP levels) ×5");
        table = table(helper, player, spell, 0, 2, 5, Map.of(), 2);
        CompoundTag before = table.saveWithoutMetadata();
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.INVALID
                && before.equals(table.saveWithoutMetadata()), "A stale Spellbinding offer activated at zero XP");
        player.experienceProgress = .7F;
        TheoryCard dark = card("dark_whispers", player, 1, true);
        table = table(helper, player, dark, 0, 2, 5, Map.of(), 2);
        accepted(helper, table, player);
        helper.assertTrue(player.experienceLevel == 0 && player.experienceProgress == 0
                && KnowledgeStore.get(player).normalWarp() == 1 && KnowledgeStore.get(player).temporaryWarp() == 0,
                "Zero-level Dark Whispers must reset XP progress and grant normal warp");
        for (String id : List.of("glyphs", "portal", "revelation", "realization")) {
            ServerPlayer next = player(helper, true);
            TheoryCard eldritch = card(id, next, 1, true);
            table = table(helper, next, eldritch, 0, 2, 5, Map.of(), 2);
            accepted(helper, table, next);
            PlayerKnowledge knowledge = KnowledgeStore.get(next);
            helper.assertTrue(knowledge.temporaryWarp() == 5 && knowledge.permanentWarp() == 0,
                    "Wrong warp compartment " + id);
            if (id.equals("portal") || id.equals("revelation"))
                helper.assertTrue(knowledge.normalWarp() == 1, "Missing normal warp " + id);
            if (id.equals("glyphs")) helper.assertTrue(knowledge.normalWarp() == 0, "Glyphs invented normal warp");
            if (id.equals("revelation")) helper.assertTrue(table.session().penaltyStart() == 1, "Revelation lost reward extension");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scriptingKeepsOriginalEmptyPaperAndLastInkUseQuirks(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard card = card("scripting", player, 1, false);
        ResearchTableBlockEntity table = table(helper, player, card, 99, 0, 5, Map.of(), 1);
        accepted(helper, table, player);
        helper.assertTrue(table.getItem(0).getDamageValue() == 100 && table.getItem(1).isEmpty()
                && table.session().totals().get("GOLEMANCY") == 25, "Scripting hardened away confirmed BETA26 EMPTY/last-ink behavior");
        table = table(helper, player, card, 100, 2, 5, Map.of(), 1);
        CompoundTag before = table.saveWithoutMetadata();
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.MISSING_RESOURCES
                && before.equals(table.saveWithoutMetadata()), "Already exhausted ink activated Scripting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bonusAndProgressOverflowRejectBeforePaperInkItemsXpOrWarp(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard card = card("sculpting", player, 1, false);
        supplies(player, card);
        ResearchTableBlockEntity table = table(helper, player, card, 0, 2, 5, Map.of("GOLEMANCY", Integer.MAX_VALUE), 1);
        CompoundTag before = table.saveWithoutMetadata();
        ListTag inventory = player.getInventory().save(new ListTag());
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.INVALID
                && before.equals(table.saveWithoutMetadata()) && inventory.equals(player.getInventory().save(new ListTag())),
                "Overflow paid Sculpting's clay/ink before checking progress");
        table = table(helper, player, card("focus", player, 1, false), 0, 2, 5, Map.of(), 1);
        CompoundTag saved = table.saveWithoutMetadata();
        saved.getCompound("Session").putInt("BonusDraws", Integer.MAX_VALUE);
        table.load(saved);
        before = table.saveWithoutMetadata();
        helper.assertTrue(table.select(player, table.revision(), 0) == TheoryResult.INVALID
                && before.equals(table.saveWithoutMetadata()), "Bonus overflow partially applied Focus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyCurioVariantPaysOnceAndAwardsItsOriginalCategory(GameTestHelper helper) {
        Map<String, String> categories = Map.of("arcane", "AUROMANCY", "preserved", "ALCHEMY",
                "ancient", "GOLEMANCY", "eldritch", "ELDRITCH", "knowledge", "INFUSION", "twisted", "ARTIFICE",
                "rites", "ELDRITCH");
        for (var variant : categories.entrySet()) {
            ServerPlayer player = player(helper, true);
            ItemStack curio = CatalogModule.stack("curio_" + variant.getKey());
            curio.setCount(2);
            curio.setHoverName(Component.literal("Pinned " + variant.getKey()));
            player.getInventory().setItem(7, curio);
            TheoryCard card = card("curio", player, 11, false);
            helper.assertTrue(card != null && card.curioType().equals(variant.getKey())
                    && card.requiredItems().get(0).stack().getCount() == 1, "Curio did not snapshot one exact inventory variant");
            ResearchTableBlockEntity table = table(helper, player, card, 0, 2, 5, Map.of(), 19);
            accepted(helper, table, player);
            int target = table.session().totals().getOrDefault(variant.getValue(), 0);
            int low = variant.getKey().equals("rites") ? 15 : 25;
            int high = variant.getKey().equals("rites") ? 25 : 40;
            helper.assertTrue(target >= low && target <= high && player.getInventory().getItem(7).getCount() == 1,
                    "Curio lost its category range or consumed more than one " + variant.getKey());
            int total = table.session().totals().values().stream().mapToInt(Integer::intValue).sum();
            helper.assertTrue(total >= 35 && total <= 45 && table.session().bonusDraws() <= 2,
                    "Curio lost base/random category or two independent bonus bounds " + variant.getKey());
            helper.assertTrue(KnowledgeStore.get(player).actualWarp() == 0 && KnowledgeStore.get(player).temporaryWarp() == 0,
                    "CardCurio invented ItemCurio.use warp effects");
        }
        helper.succeed();
    }

    private static TheoryCard targeting(String id, ServerPlayer player, net.minecraft.world.item.Item item) {
        for (long seed = 0; seed < 2000; seed++) {
            TheoryCard card = card(id, player, seed, false);
            if (card != null && card.parameterStack().is(item)) return card;
        }
        return null;
    }

    @GameTest(template = "empty")
    public static void oreGroupsPreserveWoolAndChestEquivalenceWhilePristineToolDamageStaysExact(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        TheoryCard wool = targeting("infuse", player, Items.WHITE_WOOL);
        helper.assertTrue(wool != null, "Seeded Infuse options omitted vanilla wool");
        supplies(player, wool);
        player.getInventory().setItem(0, new ItemStack(Items.PURPLE_WOOL));
        helper.assertTrue(wool.hasRequiredItems(player.getInventory()), "Original OreDictionary wool group became white-only");
        ResearchTableBlockEntity table = table(helper, player, wool, 0, 2, 5, Map.of(), 9);
        accepted(helper, table, player);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Infuse did not consume equivalent colored wool");
        TheoryCard chest = targeting("tinker", player, Items.ENDER_CHEST);
        helper.assertTrue(chest != null, "Seeded Tinker options omitted ender chest");
        supplies(player, chest);
        player.getInventory().setItem(0, new ItemStack(Items.TRAPPED_CHEST));
        helper.assertTrue(chest.hasRequiredItems(player.getInventory()), "Original common chest ore group disappeared");
        table = table(helper, player, chest, 0, 2, 5, Map.of(), 9);
        accepted(helper, table, player);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.TRAPPED_CHEST), "Presence-only Tinker consumed equivalent chest");
        TheoryCard bow = targeting("infuse", player, Items.BOW);
        helper.assertTrue(bow != null, "Seeded Infuse options omitted bow");
        supplies(player, bow);
        ItemStack named = new ItemStack(Items.BOW);
        named.setHoverName(Component.literal("Pristine modern Damage NBT"));
        player.getInventory().setItem(0, named);
        helper.assertTrue(bow.hasRequiredItems(player.getInventory()), "Modern Damage:0 turned original no-NBT template into strict custom NBT");
        named.setDamageValue(1);
        helper.assertTrue(!bow.hasRequiredItems(player.getInventory()), "A damaged bow matched exact pristine BETA26 option");
        helper.succeed();
    }
}
