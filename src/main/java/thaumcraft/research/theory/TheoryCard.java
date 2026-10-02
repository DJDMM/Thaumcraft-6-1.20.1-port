package thaumcraft.research.theory;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.research.celestial.CelestialModule;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCategories;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Objects;

/** Immutable, initialized view of the twelve currently ported TC6 BETA26 cards. */
public final class TheoryCard {
    private static final List<String> IDS = List.of("study", "analyze", "balance", "notation",
            "ponder", "rethink", "reject", "experimentation", "inspired", "celestial", "enchantment", "beacon");

    private final String id;
    private final long seed;
    private final boolean fromAid;
    private final String category;
    private final String sourceCategory;
    private final String targetCategory;
    private final int amount;
    private final int md1;
    private final int md2;

    private TheoryCard(String id, long seed, boolean fromAid, String category,
                       String sourceCategory, String targetCategory, int amount) {
        this(id, seed, fromAid, category, sourceCategory, targetCategory, amount, -1, -1);
    }

    private TheoryCard(String id, long seed, boolean fromAid, String category,
                       String sourceCategory, String targetCategory, int amount, int md1, int md2) {
        this.id = id;
        // TC6 uses Math.abs; avoid its Long.MIN_VALUE corner case in persisted modern cards.
        this.seed = seed == Long.MIN_VALUE ? 0 : Math.abs(seed);
        this.fromAid = fromAid;
        this.category = category;
        this.sourceCategory = sourceCategory;
        this.targetCategory = targetCategory;
        this.amount = amount;
        this.md1 = md1;
        this.md2 = md2;
    }

    public String id() { return id; }
    public long seed() { return seed; }
    public String category() { return category; }
    public boolean fromAid() { return fromAid; }
    public static List<String> ids() { return IDS; }
    public int md1() { return md1; }
    public int md2() { return md2; }

    public int cost() {
        return switch (id) {
            case "study", "balance", "notation", "celestial", "enchantment" -> 1;
            case "beacon" -> -2;
            case "rethink" -> -1;
            case "reject" -> 0;
            default -> 2;
        };
    }

    public boolean aidOnly() { return id.equals("study") || id.equals("notation")
            || id.equals("enchantment") || id.equals("beacon"); }

    /** The accessor also copies, so callers cannot mutate a persisted card's requirement view. */
    public record RequiredItem(ItemStack stack, boolean consumed) {
        public RequiredItem { stack = Objects.requireNonNull(stack, "stack").copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public List<RequiredItem> requiredItems() {
        return id.equals("celestial") ? List.of(new RequiredItem(CelestialModule.note(md1), true),
                new RequiredItem(CelestialModule.note(md2), true)) : List.of();
    }

    public int requiredLevels() { return id.equals("enchantment") ? 5 : 0; }
    public boolean hasRequiredItems(Inventory inventory) { return requiredItemPlan(inventory) != null; }

    /** Original payment searches mainInventory in slot order, excluding armor and offhand.
     * Celestial notes are different item IDs in the port; extra names/NBT remain irrelevant,
     * matching BETA26's no-NBT note template with its relaxed inventory filter.
     * Reserve all requirements together before allowing any consumption.
     */
    int[] requiredItemPlan(Inventory inventory) {
        List<RequiredItem> requirements = requiredItems();
        if (inventory == null) return requirements.isEmpty() ? new int[0] : null;
        int[] reserved = new int[inventory.items.size()];
        int[] consumed = new int[reserved.length];
        for (RequiredItem requirement : requirements) {
            ItemStack wanted = requirement.stack();
            if (wanted.isEmpty()) return null;
            int remaining = wanted.getCount();
            for (int slot = 0; slot < reserved.length && remaining > 0; slot++) {
                ItemStack found = inventory.items.get(slot);
                if (found.isEmpty() || !found.is(wanted.getItem())) continue;
                int available = Math.max(0, found.getCount() - reserved[slot]);
                int count = Math.min(remaining, available);
                reserved[slot] += count;
                if (requirement.consumed()) consumed[slot] += count;
                remaining -= count;
            }
            if (remaining > 0) return null;
        }
        return consumed;
    }

    public Component title() {
        return switch (id) {
            case "study", "analyze" -> Component.translatable("card." + id + ".name",
                    categoryName(category).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));
            case "reject" -> Component.translatable("card.reject.name",
                    categoryName(targetCategory).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));
            default -> Component.translatable("card." + id + ".name");
        };
    }

    public Component description() {
        return switch (id) {
            case "study" -> Component.translatable("card.study.text", categoryName(category));
            case "analyze" -> Component.translatable("card.analyze.text",
                    categoryName(category), categoryName("BASICS"));
            case "notation" -> Component.translatable("card.notation.text",
                    categoryName(sourceCategory), categoryName(targetCategory));
            case "reject" -> Component.translatable("card.reject.text", categoryName(targetCategory));
            case "inspired" -> Component.translatable("card.inspired.text", amount, categoryName(category));
            case "celestial" -> Component.translatable("card.celestial.text", categoryName(category));
            default -> Component.translatable("card." + id + ".text");
        };
    }

    private static net.minecraft.network.chat.MutableComponent categoryName(String category) {
        return Component.translatable("tc.research_category." + category).withStyle(ChatFormatting.BOLD);
    }

    String sourceCategory() { return sourceCategory; }
    String targetCategory() { return targetCategory; }
    int amount() { return amount; }

    static TheoryCard initialize(String id, long seed, boolean fromAid, TheorySession session,
                                 PlayerKnowledge knowledge) {
        if (!IDS.contains(id)) return null;
        TheoryCard card = new TheoryCard(id, seed, fromAid, null, null, null, 0);
        Random random = new Random(card.seed);
        switch (id) {
            case "study": {
                List<String> available = session.availableCategories(knowledge);
                if (available.isEmpty()) return null;
                return new TheoryCard(id, seed, fromAid, available.get(random.nextInt(available.size())),
                        null, null, 0);
            }
            case "analyze":
                /* Confirmed with javap against the official BETA26 JAR: initialize reads
                 * researchCategories.get(this.cat) while cat is null, not rc.key. Thus the
                 * uncategorized Observation bucket must be positive. Our knowledge model
                 * only holds named TC6 categories; ordinary BETA26 draws reject this card.
                 * Its real activation is still supported for a loaded, initialized card.
                 */
                return null;
            case "balance":
                return session.canBalance() ? card : null;
            case "notation": {
                if (session.totals().size() < 2) return null;
                int lowest = Integer.MAX_VALUE;
                int highest = 0;
                String lowCategory = null;
                String highCategory = null;
                // TC6 TreeMap iteration supplies alphabetical tie breaking.
                for (var total : session.totals().entrySet()) {
                    if (total.getValue() < lowest) {
                        lowest = total.getValue();
                        lowCategory = total.getKey();
                    }
                    if (total.getValue() > highest) {
                        highest = total.getValue();
                        highCategory = total.getKey();
                    }
                }
                if (lowest <= 0 || lowCategory == null || lowCategory.equals(highCategory)) return null;
                return new TheoryCard(id, seed, fromAid, null, lowCategory, highCategory, 0);
            }
            case "ponder":
                return session.canPonder() ? card : null;
            case "rethink":
                return session.totalProgress() >= 10 ? card : null;
            case "reject": {
                List<String> available = new ArrayList<>(session.totals().keySet());
                available.removeAll(session.blocked());
                if (available.isEmpty()) return null;
                return new TheoryCard(id, seed, fromAid, null, null,
                        available.get(random.nextInt(available.size())), 0);
            }
            case "inspired": {
                String category = null;
                int highest = 0;
                for (var total : session.totals().entrySet()) {
                    if (total.getValue() > highest) {
                        highest = total.getValue();
                        category = total.getKey();
                    }
                }
                if (category == null) return null;
                return new TheoryCard(id, seed, fromAid, category, null, null, 10 + highest / 2);
            }
            case "celestial": {
                // Binary-confirmed: known/opened CELESTIALSCANNING, not strictly completed.
                if (session.totals().isEmpty() || !knowledge.isResearchKnown("CELESTIALSCANNING")) return null;
                int first = random.nextInt(13);
                int second = first;
                while (second == first) second = random.nextInt(13);
                String category = null;
                int highest = 0;
                for (var total : session.totals().entrySet()) {
                    if (total.getValue() > highest) {
                        highest = total.getValue();
                        category = total.getKey();
                    }
                }
                return category == null ? null : new TheoryCard(id, seed, fromAid, category,
                        null, null, 0, first, second);
            }
            default:
                return card;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putLong("Seed", seed);
        tag.putBoolean("FromAid", fromAid);
        if (category != null) tag.putString("Category", category);
        if (sourceCategory != null) tag.putString("SourceCategory", sourceCategory);
        if (targetCategory != null) tag.putString("TargetCategory", targetCategory);
        tag.putInt("Amount", amount);
        if (id.equals("celestial")) {
            tag.putInt("md1", md1);
            tag.putInt("md2", md2);
        }
        return tag;
    }

    /** Unknown or malformed card data is rejected rather than rerolled. */
    public static TheoryCard load(CompoundTag tag) {
        if (tag == null || !IDS.contains(tag.getString("Id"))) return null;
        String id = tag.getString("Id");
        String category = readCategory(tag, "Category");
        String source = readCategory(tag, "SourceCategory");
        String target = readCategory(tag, "TargetCategory");
        int amount = tag.getInt("Amount");
        if ((id.equals("study") || id.equals("analyze") || id.equals("inspired") || id.equals("celestial")) && category == null) return null;
        if (id.equals("analyze") && category.equals("BASICS")) return null;
        if (id.equals("notation") && (source == null || target == null || source.equals(target))) return null;
        if (id.equals("reject") && target == null) return null;
        if (id.equals("inspired") && amount < 10) return null;
        int first = id.equals("celestial") ? tag.getInt("md1") : -1;
        int second = id.equals("celestial") ? tag.getInt("md2") : -1;
        if (id.equals("celestial") && (!tag.contains("md1", net.minecraft.nbt.Tag.TAG_INT)
                || !tag.contains("md2", net.minecraft.nbt.Tag.TAG_INT) || first < 0 || first > 12
                || second < 0 || second > 12 || first == second)) return null;
        TheoryCard result = new TheoryCard(id, tag.getLong("Seed"), tag.getBoolean("FromAid"),
                category, source, target, amount, first, second);
        if (result.aidOnly() && !result.fromAid) return null;
        return result;
    }

    private static String readCategory(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return ResearchCategories.contains(value) ? value : null;
    }
}
