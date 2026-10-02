package thaumcraft.research.theory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCategories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Detached effects of the additional 21 BETA26 cards. No payment or player mutation occurs here. */
public final class TheoryCardEffects {
    private TheoryCardEffects() {}

    public record Plan(Map<String, Integer> totals, Set<String> blocked, int bonusDraws, int penaltyStart,
                       int temporaryWarp, int normalWarp, int levelsSpent, int inspirationRefund,
                       List<ItemStack> outputs) {
        public Plan {
            totals = Collections.unmodifiableMap(new TreeMap<>(totals));
            blocked = Collections.unmodifiableSet(new LinkedHashSet<>(blocked));
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
        @Override public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    }

    public static boolean canSelect(TheoryCard card, TheorySession session, PlayerKnowledge knowledge,
                                    Inventory inventory, int levels) {
        return switch (card.id()) {
            case "spellbinding" -> levels > 0;
            case "synergy" -> synergyTotal(session.totals()) >= 15;
            default -> true;
        };
    }

    /** Session.select commits the returned state only after item, level and overflow preflight. */
    public static Plan plan(TheoryCard card, TheorySession session, PlayerKnowledge knowledge,
                            ServerPlayer player, RandomSource random) {
        if (card == null || session == null || knowledge == null || player == null || random == null
                || !canSelect(card, session, knowledge, player.getInventory(), player.experienceLevel)) return null;
        Map<String, Integer> totals = new TreeMap<>(session.totals());
        Set<String> blocked = new LinkedHashSet<>(session.blocked());
        int bonus = session.bonusDraws(), penalty = session.penaltyStart();
        int normalWarp = 0, temporaryWarp = 0, levelsSpent = 0, refund = 0;
        List<ItemStack> outputs = new ArrayList<>();
        try {
            switch (card.id()) {
                case "curio" -> {
                    add(totals, "BASICS", 5);
                    add(totals, category(random), 5);
                    switch (card.curioType()) {
                        case "arcane" -> add(totals, "AUROMANCY", between(random, 25, 35));
                        case "preserved" -> add(totals, "ALCHEMY", between(random, 25, 35));
                        case "ancient" -> add(totals, "GOLEMANCY", between(random, 25, 35));
                        case "eldritch" -> add(totals, "ELDRITCH", between(random, 25, 35));
                        case "knowledge" -> add(totals, "INFUSION", between(random, 25, 35));
                        case "twisted" -> add(totals, "ARTIFICE", between(random, 25, 35));
                        case "rites" -> {
                            add(totals, "ELDRITCH", between(random, 15, 20));
                            add(totals, "AUROMANCY", between(random, 10, 15));
                        }
                        default -> add(totals, "BASICS", between(random, 25, 35));
                    }
                    if (random.nextBoolean()) bonus = Math.incrementExact(bonus);
                    if (random.nextBoolean()) bonus = Math.incrementExact(bonus);
                }
                case "concentrate" -> {
                    add(totals, "ALCHEMY", 15);
                    bonus = Math.incrementExact(bonus);
                    if (random.nextFloat() < 0.33d) refund = 1;
                }
                case "reactions" -> {
                    add(totals, "ALCHEMY", 25);
                    if (random.nextFloat() < 0.33d) refund = 1;
                }
                case "synthesis" -> {
                    add(totals, "ALCHEMY", 40);
                    if (random.nextFloat() < 0.33d) refund = 1;
                    ItemStack result = AspectCrystalItem.create(card.aspect("aspect3"));
                    if (result.isEmpty()) return null;
                    outputs.add(result);
                }
                case "calibrate" -> {
                    add(totals, "ARTIFICE", 15);
                    bonus = Math.incrementExact(bonus);
                }
                case "mind_over_matter" -> add(totals, "ARTIFICE", card.value());
                case "tinker" -> add(totals, "ARTIFICE", between(random, card.value(), Math.addExact(card.value(), 10)));
                case "measure" -> {
                    add(totals, "INFUSION", 15);
                    bonus = Math.incrementExact(bonus);
                }
                case "channel" -> add(totals, "INFUSION", 25);
                case "infuse" -> add(totals, "INFUSION", card.value());
                case "focus" -> {
                    add(totals, "AUROMANCY", 15);
                    bonus = Math.incrementExact(bonus);
                }
                case "awareness" -> {
                    add(totals, "AUROMANCY", 20);
                    if (random.nextFloat() < 0.33d) {
                        add(totals, "ELDRITCH", between(random, 1, 5));
                        normalWarp = 1;
                    }
                }
                case "spellbinding" -> {
                    levelsSpent = Math.min(5, player.experienceLevel);
                    if (levelsSpent <= 0) return null;
                    add(totals, "AUROMANCY", levelsSpent * 5);
                }
                case "sculpting" -> {
                    add(totals, "GOLEMANCY", 20);
                    bonus = Math.incrementExact(bonus);
                }
                // Table-side callbacks are separate from the player's main inventory.
                case "scripting" -> add(totals, "GOLEMANCY", 25);
                case "synergy" -> {
                    if (synergyTotal(totals) < 15) return null;
                    int remaining = 15;
                    while (remaining > 0) {
                        for (String category : List.of("ARTIFICE", "ALCHEMY", "INFUSION")) {
                            if (totals.getOrDefault(category, 0) <= 0) continue;
                            add(totals, category, -1);
                            if (--remaining == 0) break;
                        }
                    }
                    add(totals, "GOLEMANCY", 30);
                    penalty = Math.incrementExact(penalty);
                }
                case "dark_whispers" -> {
                    int level = player.experienceLevel;
                    // Original removes l + 10 levels even at zero, resetting XP remainder.
                    levelsSpent = Math.addExact(level, 10);
                    if (level > 0) {
                        for (String category : ResearchCategories.keys()) {
                            if (random.nextBoolean()) continue;
                            add(totals, category, between(random, 0, Math.max(1, (int) Math.sqrt(level))));
                        }
                    }
                    add(totals, "ELDRITCH", between(random, Math.max(1, level / 5), Math.max(5, level / 2)));
                    normalWarp = Math.max(1, (int) Math.sqrt(level));
                    if (random.nextBoolean()) bonus = Math.incrementExact(bonus);
                }
                case "glyphs" -> {
                    add(totals, category(random), between(random, 10, 20));
                    add(totals, "ELDRITCH", between(random, 10, 20));
                    temporaryWarp = 5;
                }
                case "portal" -> {
                    add(totals, category(random), between(random, 5, 10));
                    add(totals, category(random), between(random, 5, 10));
                    add(totals, "ELDRITCH", between(random, 5, 10));
                    bonus = Math.addExact(bonus, 2);
                    temporaryWarp = 5;
                    normalWarp = 1;
                }
                case "revelation" -> {
                    add(totals, category(random), between(random, 5, 10));
                    add(totals, "ELDRITCH", 30);
                    temporaryWarp = 5;
                    normalWarp = 1;
                    penalty = Math.incrementExact(penalty);
                }
                case "realization" -> {
                    add(totals, category(random), between(random, 5, 10));
                    add(totals, category(random), between(random, 5, 10));
                    add(totals, "ELDRITCH", 15);
                    temporaryWarp = 5;
                    if (random.nextBoolean()) normalWarp = 1;
                }
                default -> { return null; }
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        return new Plan(totals, blocked, bonus, penalty, temporaryWarp, normalWarp,
                levelsSpent, refund, outputs);
    }

    private static long synergyTotal(Map<String, Integer> totals) {
        return totals.getOrDefault("ARTIFICE", 0) + (long) totals.getOrDefault("ALCHEMY", 0)
                + totals.getOrDefault("INFUSION", 0);
    }
    private static String category(RandomSource random) {
        List<String> categories = ResearchCategories.keys();
        return categories.get(random.nextInt(categories.size()));
    }
    private static int between(RandomSource random, int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }
    private static void add(Map<String, Integer> target, String category, int amount) {
        int next = Math.addExact(target.getOrDefault(category, 0), amount);
        if (next <= 0) target.remove(category); else target.put(category, next);
    }
}
