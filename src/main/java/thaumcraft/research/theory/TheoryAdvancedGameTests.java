package thaumcraft.research.theory;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.celestial.CelestialModule;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Card-focused transaction tests for the additional BETA26 card and vanilla aid slice. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryAdvancedGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "advanced_theory_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static PlayerKnowledge celestialKnown() {
        CompoundTag saved = new PlayerKnowledge().save();
        CompoundTag stages = new CompoundTag();
        stages.putInt("CELESTIALSCANNING", 1);
        saved.put("ResearchStages", stages);
        return PlayerKnowledge.load(saved);
    }

    private static CompoundTag card(String id, String category, boolean aid) {
        CompoundTag card = new CompoundTag();
        card.putString("Id", id);
        card.putLong("Seed", 12345);
        card.putBoolean("FromAid", aid);
        card.putInt("Amount", 0);
        if (category != null) card.putString("Category", category);
        return card;
    }

    private static CompoundTag celestial(int first, int second) {
        CompoundTag card = card("celestial", "BASICS", false);
        card.putInt("md1", first);
        card.putInt("md2", second);
        return card;
    }

    private static TheorySession session(ServerPlayer player, int inspiration, Map<String, Integer> totals,
                                         CompoundTag choice, long seed) {
        Set<String> aids = choice != null && choice.getBoolean("FromAid")
                ? Set.of(choice.getString("Id").equals("beacon") ? TheoryAids.BEACON : TheoryAids.ENCHANTMENT_TABLE) : Set.of();
        CompoundTag saved = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), aids, RandomSource.create(1)).save();
        saved.putInt("Inspiration", inspiration);
        saved.putLong("RandomSeed", seed);
        CompoundTag progress = new CompoundTag();
        totals.forEach(progress::putInt);
        saved.put("Totals", progress);
        ListTag choices = new ListTag();
        if (choice != null) choices.add(choice);
        saved.put("Choices", choices);
        return TheorySession.load(saved);
    }

    private static ListTag inventory(ServerPlayer player) { return player.getInventory().save(new ListTag()); }

    private static void notes(ServerPlayer player, int first, int second) {
        player.getInventory().setItem(3, CelestialModule.note(first));
        player.getInventory().setItem(9, CelestialModule.note(second));
    }

    @GameTest(template = "empty")
    public static void celestialInitializationUsesKnownResearchDistinctSeededNotesAndAlphabeticLeader(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession totals = session(player, 5, Map.of("BASICS", 30, "ALCHEMY", 30, "ARTIFICE", 20), null, 13);
        PlayerKnowledge knowledge = celestialKnown();
        helper.assertTrue(knowledge.isResearchKnown("CELESTIALSCANNING")
                        && !knowledge.isResearchCompleteStrict("CELESTIALSCANNING"), "Fixture must be opened, incomplete celestial research");
        helper.assertTrue(TheoryCard.initialize("celestial", 5, false, totals, new PlayerKnowledge()) == null,
                "Celestial initialized before its research was known");
        helper.assertTrue(TheoryCard.initialize("celestial", 5, false,
                        session(player, 5, Map.of(), null, 13), knowledge) == null, "Celestial initialized without theory totals");
        Set<Integer> seen = new java.util.HashSet<>();
        for (long seed = 0; seed < 80; seed++) {
            TheoryCard card = TheoryCard.initialize("celestial", seed, false, totals, knowledge);
            helper.assertTrue(card != null && card.category().equals("ALCHEMY") && card.cost() == 1 && !card.aidOnly()
                            && card.md1() >= 0 && card.md1() <= 12 && card.md2() >= 0 && card.md2() <= 12
                            && card.md1() != card.md2(), "Celestial lost its known-only gate, leader tie or distinct note range");
            helper.assertTrue(card.save().equals(TheoryCard.load(card.save()).save()), "Celestial targets rerolled during card reload");
            seen.add(card.md1()); seen.add(card.md2());
        }
        helper.assertTrue(seen.size() == 13, "Seeded celestial requirements did not cover all thirteen variants");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threeAidPoolsRemainFiniteWeightedAndIndependentOfBeaconPower(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession session = TheorySession.create(player.getUUID(), KnowledgeStore.get(player),
                Set.of(TheoryAids.BEACON, TheoryAids.ENCHANTMENT_TABLE, TheoryAids.BOOKSHELF), RandomSource.create(7));
        helper.assertTrue(session.inspiration() == 2 && session.inspirationStart() == 5 && session.aids().size() == 3,
                "Three selected aid keys did not charge exactly three inspiration");
        helper.assertTrue(session.aidCardsRemaining().equals(List.of("balance", "notation", "notation", "study", "study", "study", "enchantment", "beacon")),
                "Aid pools lost their exact weighted card lists or canonical order");
        helper.assertTrue(session.save().equals(TheorySession.load(session.save()).save()), "Three aid pools did not survive session reload");
        helper.assertTrue(TheoryAids.matches(TheoryAids.BEACON, Blocks.BEACON.defaultBlockState())
                        && TheoryAids.matches(TheoryAids.ENCHANTMENT_TABLE, Blocks.ENCHANTING_TABLE.defaultBlockState())
                        && !TheoryAids.matches(TheoryAids.BEACON, Blocks.GLASS.defaultBlockState()), "Vanilla aid matching gained an activation or power condition");
        TheoryCard enchantment = TheoryCard.initialize("enchantment", 1, true, session, new PlayerKnowledge());
        TheoryCard beacon = TheoryCard.initialize("beacon", 1, true, session, new PlayerKnowledge());
        helper.assertTrue(enchantment != null && enchantment.aidOnly() && enchantment.requiredLevels() == 5
                        && beacon != null && beacon.aidOnly() && beacon.cost() == -2,
                "Aid card initialization incorrectly requires XP or another research gate");
        CompoundTag old = TheorySession.create(player.getUUID(), new PlayerKnowledge(), Set.of(TheoryAids.BOOKSHELF), RandomSource.create(1)).save();
        helper.assertTrue(TheorySession.load(old).save().equals(old), "Old version-one Bookshelf session stopped loading");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialRequiresBothExactNotesFromMainInventoryBeforeAnyPayment(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession session = session(player, 5, Map.of("BASICS", 20), celestial(0, 5), 9);
        player.getInventory().setItem(3, CelestialModule.note(0));
        player.getInventory().setItem(9, CelestialModule.note(6));
        player.getInventory().offhand.set(0, CelestialModule.note(5));
        player.getInventory().armor.set(0, CelestialModule.note(5));
        CompoundTag before = session.save(), knowledge = KnowledgeStore.get(player).save();
        ListTag inventory = inventory(player);
        helper.assertTrue(!session.canSelect(KnowledgeStore.get(player), player.getInventory(), 0, 0)
                        && !session.select(player, 0), "Wrong moon phase, offhand or armor paid an exact main-inventory requirement");
        helper.assertTrue(session.save().equals(before) && inventory(player).equals(inventory)
                        && KnowledgeStore.get(player).save().equals(knowledge), "Failed note payment changed cards, items, RNG or knowledge");
        player.getInventory().setItem(9, CelestialModule.note(5));
        ItemStack named = player.getInventory().getItem(3);
        named.setHoverName(Component.literal("Named sun note"));
        named.getOrCreateTag().putString("UnrelatedMarker", "kept until payment");
        helper.assertTrue(session.select(player, 0), "Additional note name/NBT prevented original relaxed payment");
        helper.assertTrue(player.getInventory().getItem(3).isEmpty() && player.getInventory().getItem(9).isEmpty()
                        && !player.getInventory().offhand.get(0).isEmpty() && !player.getInventory().armor.get(0).isEmpty()
                        && session.inspiration() == 4 && session.penaltyStart() == 1 && session.bonusDraws() == 1,
                "Celestial failed to consume the two main-inventory notes once or altered excluded inventory slots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialPaysSlotOrderAndRejectsReplayWithPersistentTargets(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession session = session(player, 5, Map.of("BASICS", 20), celestial(0, 12), 44);
        ItemStack firstSun = CelestialModule.note(0); firstSun.setCount(2);
        player.getInventory().setItem(0, firstSun);
        player.getInventory().setItem(8, CelestialModule.note(0));
        ItemStack moon = CelestialModule.note(12); moon.setCount(3);
        player.getInventory().setItem(35, moon);
        player.getInventory().setItem(7, new ItemStack(Items.PAPER, 4));
        CompoundTag persisted = session.save();
        session = TheorySession.load(persisted);
        helper.assertTrue(session.save().equals(persisted) && session.select(player, 0), "Restored exact-note offer changed or failed to activate");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 1 && player.getInventory().getItem(8).getCount() == 1
                        && player.getInventory().getItem(35).getCount() == 2 && player.getInventory().getItem(7).getCount() == 4,
                "Celestial consumed a later matching slot or unrelated paper");
        CompoundTag after = session.save(), knowledge = KnowledgeStore.get(player).save();
        ListTag inventory = inventory(player);
        helper.assertTrue(!session.select(player, 0) && session.save().equals(after) && inventory(player).equals(inventory)
                        && KnowledgeStore.get(player).save().equals(knowledge), "Replayed selection duplicated effects or note payment");
        helper.assertTrue(session.lastCard().md1() == 0 && session.lastCard().md2() == 12
                        && TheorySession.load(after).lastCard().save().equals(session.lastCard().save()), "Selected note metadata did not survive history reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialSunMoonAndStarsApplyOnlyTheirBinaryBranches(GameTestHelper helper) {
        int[][] pairs = {{0, 5}, {0, 1}, {1, 2}, {1, 12}, {5, 12}};
        for (int[] pair : pairs) {
            ServerPlayer player = player(helper);
            TheorySession session = session(player, 5, Map.of("BASICS", 20), celestial(pair[0], pair[1]), 19);
            notes(player, pair[0], pair[1]);
            helper.assertTrue(session.select(player, 0), "Eligible celestial note pair failed");
            int gain = session.totals().get("BASICS") - 20;
            boolean sun = pair[0] == 0 || pair[1] == 0;
            boolean moon = pair[0] > 4 || pair[1] > 4;
            boolean stars = pair[0] > 0 && pair[0] < 5 || pair[1] > 0 && pair[1] < 5;
            int warp = KnowledgeStore.get(player).temporaryWarp();
            // Seed 19 fixes the first 25..50 roll at 50 and the one 0..5 star roll at 2.
            // A pair of star notes must still apply that branch once, rather than twice.
            helper.assertTrue(gain == 50 && session.penaltyStart() == (sun ? 1 : 0)
                            && session.bonusDraws() == (moon ? 1 : 0), "Celestial category gain or sun/moon branch changed");
            helper.assertTrue(warp == (stars ? 2 : 0) && session.totals().getOrDefault("ELDRITCH", 0) == warp * 2,
                    "Celestial star pair doubled its branch or lost its coupled Eldritch/temporary-warp formula");
            helper.assertTrue(PlayerKnowledge.load(KnowledgeStore.get(player).save()).temporaryWarp() == warp,
                    "Celestial temporary warp did not survive knowledge persistence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialOverflowCannotConsumeNotesOrApplyWarpOrSunMoonBenefits(GameTestHelper helper) {
        for (int[] pair : new int[][] {{0, 5}, {0, 1}, {1, 12}}) {
            ServerPlayer player = player(helper);
            TheorySession session = session(player, 5, Map.of("BASICS", Integer.MAX_VALUE), celestial(pair[0], pair[1]), 19);
            notes(player, pair[0], pair[1]);
            CompoundTag before = session.save(), knowledge = KnowledgeStore.get(player).save();
            ListTag inventory = inventory(player);
            helper.assertTrue(!session.select(player, 0) && session.save().equals(before)
                            && inventory(player).equals(inventory) && KnowledgeStore.get(player).save().equals(knowledge),
                    "Overflow paid celestial notes or partially committed its effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantmentNeedsFiveLevelsAndOverflowPrecedesItsXpPayment(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession session = session(player, 4, Map.of(), card("enchantment", null, true), 31);
        player.experienceLevel = 4;
        player.experienceProgress = 0.375F;
        player.totalExperience = 70;
        CompoundTag before = session.save();
        helper.assertTrue(!session.canSelect(KnowledgeStore.get(player), player.getInventory(), 4, 0)
                        && !session.select(player, 0) && session.save().equals(before) && player.experienceLevel == 4
                        && player.experienceProgress == 0.375F && player.totalExperience == 70,
                "Insufficient XP partially paid or applied Enchantment");
        player.experienceLevel = 5;
        TheorySession overflowing = session(player, 4, Map.of("INFUSION", Integer.MAX_VALUE), card("enchantment", null, true), 31);
        before = overflowing.save();
        helper.assertTrue(!overflowing.select(player, 0) && overflowing.save().equals(before) && player.experienceLevel == 5,
                "Enchantment overflow spent five levels before effect validation");
        helper.assertTrue(session.select(player, 0) && player.experienceLevel == 0 && session.inspiration() == 3
                        && session.totals().get("INFUSION") >= 15 && session.totals().get("INFUSION") <= 20
                        && session.totals().get("AUROMANCY") >= 15 && session.totals().get("AUROMANCY") <= 20,
                "Enchantment did not spend five whole levels for both locked-category rewards");
        helper.assertTrue(!session.select(player, 0) && player.experienceLevel == 0, "Enchantment replay spent XP twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beaconRefundCapsAtPreAidMaximumAndCounterOverflowRejectsItsWholeEffect(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        TheorySession session = session(player, 4, Map.of("BASICS", 20), card("beacon", null, true), 8);
        helper.assertTrue(session.select(player, 0) && session.inspiration() == 5 && session.inspirationStart() == 5
                        && session.bonusDraws() == 1 && session.penaltyStart() == 1 && session.totals().equals(Map.of("BASICS", 20)),
                "Beacon failed to refund two with the full pre-aid cap or altered category progress");
        TheorySession overflowing = session(player, 2, Map.of(), card("beacon", null, true), 8);
        CompoundTag saved = overflowing.save(); saved.putInt("PenaltyStart", Integer.MAX_VALUE);
        overflowing = TheorySession.load(saved);
        helper.assertTrue(!overflowing.select(player, 0) && overflowing.save().equals(saved), "Beacon counter overflow partially refunded inspiration or granted a bonus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void celestialRequirementsAreDefensiveAndMalformedMetadataIsRejected(GameTestHelper helper) {
        TheoryCard celestial = TheoryCard.load(celestial(0, 12));
        List<TheoryCard.RequiredItem> requirements = celestial.requiredItems();
        helper.assertTrue(requirements.size() == 2 && requirements.stream().allMatch(TheoryCard.RequiredItem::consumed),
                "Celestial requirements lost one of the two consumed notes");
        requirements.get(0).stack().setCount(63);
        helper.assertTrue(requirements.get(0).stack().getCount() == 1 && celestial.requiredItems().get(0).stack().getCount() == 1,
                "Requirement accessor exposed a mutable note template");
        boolean readOnly = false;
        try { requirements.clear(); } catch (UnsupportedOperationException expected) { readOnly = true; }
        helper.assertTrue(readOnly, "Requirement list is mutable");
        for (int[] pair : new int[][] {{0, 0}, {-1, 5}, {0, 13}}) {
            helper.assertTrue(TheoryCard.load(celestial(pair[0], pair[1])) == null, "Malformed or duplicate celestial metadata loaded");
        }
        CompoundTag missing = celestial(0, 12); missing.remove("md2");
        helper.assertTrue(TheoryCard.load(missing) == null, "Missing note metadata silently rerolled or defaulted");
        helper.succeed();
    }
}
