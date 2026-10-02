package thaumcraft.research.theory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchCategories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** TC6 BETA26 percentage theorycraft state; paper, ink and final delivery belong to the table. */
public final class TheorySession {
    public static final String BOOKSHELF = TheoryAids.BOOKSHELF;
    public static final String ENCHANTMENT_TABLE = TheoryAids.ENCHANTMENT_TABLE;
    public static final String BEACON = TheoryAids.BEACON;

    private final UUID owner;
    private int inspiration;
    private int inspirationStart;
    private int bonusDraws;
    private int penaltyStart;
    private int placedCards;
    private long randomSeed;
    private final Set<String> aids = new LinkedHashSet<>();
    private final List<String> aidCards = new ArrayList<>();
    private final Map<String, Integer> totals = new TreeMap<>();
    private final Set<String> blocked = new LinkedHashSet<>();
    private final List<TheoryCard> choices = new ArrayList<>();
    private TheoryCard lastCard;

    private TheorySession(UUID owner) { this.owner = owner; }

    public static TheorySession create(UUID owner, PlayerKnowledge knowledge, Set<String> aids,
                                       RandomSource random) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(aids, "aids");
        Objects.requireNonNull(random, "random");
        if (!TheoryAids.keys().containsAll(aids)) throw new IllegalArgumentException("Unsupported theory aid");
        TheorySession session = new TheorySession(owner);
        session.inspirationStart = availableInspiration(knowledge);
        session.inspiration = session.inspirationStart - aids.size();
        session.randomSeed = random.nextLong();
        // Canonical registry order avoids Set iteration changing the same seeded aid pool.
        for (String aid : TheoryAids.keys()) if (aids.contains(aid)) {
            session.aids.add(aid);
            session.aidCards.addAll(TheoryAids.cards(aid));
        }
        return session;
    }

    public static int availableInspiration(PlayerKnowledge knowledge) {
        float total = 5;
        for (String key : knowledge.researchKeys()) {
            if (!knowledge.isResearchCompleteStrict(key)) continue;
            var entry = ResearchCatalog.get(key);
            if (entry == null || entry.supported()) continue;
            if (entry.hasMeta("SPIKY")) total += 0.5f;
            if (entry.hasMeta("HIDDEN")) total += 0.1f;
        }
        return Math.min(15, Math.round(total));
    }

    public UUID owner() { return owner; }
    public int inspiration() { return inspiration; }
    public int inspirationStart() { return inspirationStart; }
    public int bonusDraws() { return bonusDraws; }
    public int penaltyStart() { return penaltyStart; }
    public int placedCards() { return placedCards; }
    public Map<String, Integer> totals() { return Collections.unmodifiableMap(totals); }
    public Set<String> blocked() { return Collections.unmodifiableSet(blocked); }
    public Set<String> aids() { return Collections.unmodifiableSet(aids); }
    public List<String> aidCardsRemaining() { return Collections.unmodifiableList(aidCards); }
    public List<TheoryCard> choices() { return Collections.unmodifiableList(choices); }
    public TheoryCard lastCard() { return lastCard; }
    public boolean complete() { return inspiration <= 0; }

    List<String> availableCategories(PlayerKnowledge knowledge) {
        return ResearchCategories.keys().stream().filter(category -> !blocked.contains(category)
                && ResearchCategories.categoryUnlocked(knowledge, category)).toList();
    }

    long totalProgress() { return totals.values().stream().mapToLong(Integer::longValue).sum(); }

    boolean canBalance() {
        long total = 0;
        int size = 0;
        for (var entry : totals.entrySet()) {
            if (blocked.contains(entry.getKey())) continue;
            total += entry.getValue();
            size++;
        }
        // Preserve the original test of blocked count, including categories no longer in totals.
        return blocked.size() < totals.size() - 1 && total >= size;
    }

    boolean canPonder() { return blocked.size() < totals.size(); }

    /** Returns false without changing the session if no eligible offer exists. */
    public boolean draw(ServerPlayer player, boolean bonus) {
        if (player == null || !owner.equals(player.getUUID()) || complete() || !choices.isEmpty()) return false;
        return draw(player, KnowledgeStore.get(player), bonus);
    }

    private boolean draw(ServerPlayer player, PlayerKnowledge knowledge, boolean bonus) {
        int count = bonus && bonusDraws > 0 ? 3 : 2;
        RandomSource random = RandomSource.create(randomSeed);
        List<TheoryCard> drawn = new ArrayList<>();
        List<String> remainingAids = new ArrayList<>(aidCards);
        Set<String> drawnIds = new LinkedHashSet<>();
        List<String> available = availableCategories(knowledge);
        // The original has a 10,000-attempt safeguard and can produce fewer than requested.
        for (int attempts = 0; attempts < 10000 && drawn.size() < count; attempts++) {
            boolean fromAid = !remainingAids.isEmpty() && random.nextFloat() <= 0.25f;
            List<String> pool = fromAid ? remainingAids : TheoryCard.ids();
            int index = random.nextInt(pool.size());
            String id = pool.get(index);
            TheoryCard card = TheoryCard.initialize(id, random.nextLong(), fromAid, this, knowledge,
                    player.getInventory(), player.experienceLevel);
            if (card == null || card.cost() > inspiration || drawnIds.contains(id)) continue;
            if (fromAid) {
                if (blocked.contains(card.category())) continue;
            } else if (card.aidOnly() || card.category() != null && !available.contains(card.category())) {
                continue;
            }
            drawn.add(card);
            drawnIds.add(id);
            // Drawn aid cards leave their finite pool even if another offer is selected.
            if (fromAid) remainingAids.remove(index);
        }
        if (drawn.isEmpty()) return false;
        if (bonus && bonusDraws > 0) bonusDraws--;
        choices.addAll(drawn);
        aidCards.clear();
        aidCards.addAll(remainingAids);
        // Persist the next draw's seed without changing it during save or client rendering.
        randomSeed = random.nextLong();
        return true;
    }

    /** Read-only eligibility for clients; selection also preflights effect arithmetic on the server. */
    public boolean canSelect(PlayerKnowledge knowledge, Inventory inventory, int levels, int index) {
        if (knowledge == null || complete() || index < 0 || index >= choices.size()
                || placedCards == Integer.MAX_VALUE) return false;
        TheoryCard card = choices.get(index);
        if (card.cost() > inspiration || levels < card.requiredLevels() || !card.hasRequiredItems(inventory)) return false;
        if (card.category() != null && (blocked.contains(card.category()) || !card.fromAid()
                && !ResearchCategories.categoryUnlocked(knowledge, card.category()))) return false;
        return switch (card.id()) {
            case "analyze" -> knowledge.completedKnowledge(KnowledgeType.OBSERVATION, card.category()) >= 1;
            case "balance" -> canBalance();
            case "ponder" -> canPonder();
            case "rethink" -> totalProgress() >= 10;
            case "reject" -> !blocked.contains(card.targetCategory()) && totals.containsKey(card.targetCategory());
            case "notation" -> totals.getOrDefault(card.sourceCategory(), 0) > 0 && totals.containsKey(card.targetCategory());
            case "inspired", "celestial" -> totals.containsKey(card.category());
            default -> TheoryCardEffects.canSelect(card, this, knowledge, inventory, levels);
        };
    }

    /** All item, XP, knowledge and arithmetic validation precedes any payment or effect. */
    public boolean select(ServerPlayer player, int index) {
        if (player == null || !owner.equals(player.getUUID()) || complete()
                || index < 0 || index >= choices.size() || placedCards == Integer.MAX_VALUE) return false;
        TheoryCard card = choices.get(index);
        PlayerKnowledge knowledge = KnowledgeStore.get(player);
        if (!canSelect(knowledge, player.getInventory(), player.experienceLevel, index)) return false;
        int[] consumedItems = card.requiredItemPlan(player.getInventory());
        if (consumedItems == null) return false;

        Map<String, Integer> nextTotals = new TreeMap<>(totals);
        Set<String> nextBlocked = new LinkedHashSet<>(blocked);
        int nextBonus = bonusDraws;
        int nextPenalty = penaltyStart;
        String debitCategory = null;
        int temporaryWarp = 0;
        int normalWarp = 0;
        int levelsSpent = card.requiredLevels();
        int inspirationRefund = 0;
        List<ItemStack> outputs = List.of();
        // Modern adaptation: session-owned RNG makes both effects and later draws survive
        // reload, and failed selection does not consume the player's random stream.
        RandomSource random = RandomSource.create(randomSeed);
        try {
            switch (card.id()) {
                case "study" -> addTotal(nextTotals, card.category(), between(random, 15, 25));
                case "analyze" -> {
                    if (knowledge.completedKnowledge(KnowledgeType.OBSERVATION, card.category()) < 1) return false;
                    debitCategory = card.category();
                    addTotal(nextTotals, "BASICS", 5);
                    addTotal(nextTotals, card.category(), between(random, 25, 50));
                }
                case "balance" -> {
                    if (!canBalance()) return false;
                    long total = 0;
                    int size = 0;
                    for (var entry : nextTotals.entrySet()) {
                        if (blocked.contains(entry.getKey())) continue;
                        total += entry.getValue();
                        size++;
                    }
                    int average = Math.toIntExact(total / size);
                    nextTotals.replaceAll((category, amount) -> blocked.contains(category) ? amount : average);
                    addTotal(nextTotals, "BASICS", 5);
                    nextPenalty = Math.incrementExact(nextPenalty);
                }
                case "notation" -> {
                    int low = nextTotals.getOrDefault(card.sourceCategory(), 0);
                    if (low <= 0 || !nextTotals.containsKey(card.targetCategory())) return false;
                    addTotal(nextTotals, card.sourceCategory(), -low);
                    addTotal(nextTotals, card.targetCategory(), low / 2 + between(random, 0, low / 2));
                }
                case "ponder" -> {
                    if (!canPonder()) return false;
                    List<String> eligible = nextTotals.keySet().stream().filter(category -> !blocked.contains(category)).toList();
                    if (eligible.isEmpty()) return false;
                    for (int point = 0; point < 25; point++) addTotal(nextTotals, eligible.get(point % eligible.size()), 1);
                    addTotal(nextTotals, "BASICS", 5);
                    nextBonus = Math.incrementExact(nextBonus);
                }
                case "rethink" -> {
                    if (totalProgress() < 10) return false;
                    int remaining = 10;
                    while (remaining > 0) {
                        // TC6 restarts alphabetical iteration when decrementing removes a category.
                        for (String category : List.copyOf(nextTotals.keySet())) {
                            addTotal(nextTotals, category, -1);
                            remaining--;
                            if (remaining == 0 || !nextTotals.containsKey(category)) break;
                        }
                    }
                    nextBonus = Math.incrementExact(nextBonus);
                    addTotal(nextTotals, "BASICS", between(random, 1, 10));
                }
                case "reject" -> {
                    if (blocked.contains(card.targetCategory()) || !totals.containsKey(card.targetCategory())) return false;
                    addTotal(nextTotals, "BASICS", 5);
                    nextBlocked.add(card.targetCategory());
                }
                case "experimentation" -> {
                    List<String> all = ResearchCategories.keys();
                    // Includes unopened and blocked categories, exactly as the original activation.
                    addTotal(nextTotals, all.get(random.nextInt(all.size())), between(random, 15, 30));
                    addTotal(nextTotals, "BASICS", between(random, 1, 10));
                }
                case "inspired" -> {
                    if (!nextTotals.containsKey(card.category())) return false;
                    addTotal(nextTotals, card.category(), card.amount());
                }
                case "celestial" -> {
                    addTotal(nextTotals, card.category(), between(random, 25, 50));
                    boolean sun = card.md1() == 0 || card.md2() == 0;
                    boolean moon = card.md1() > 4 || card.md2() > 4;
                    boolean stars = card.md1() > 0 && card.md1() < 5 || card.md2() > 0 && card.md2() < 5;
                    if (stars) {
                        temporaryWarp = between(random, 0, 5);
                        addTotal(nextTotals, "ELDRITCH", temporaryWarp * 2);
                    }
                    if (sun) nextPenalty = Math.incrementExact(nextPenalty);
                    if (moon) nextBonus = Math.incrementExact(nextBonus);
                }
                case "enchantment" -> {
                    addTotal(nextTotals, "INFUSION", between(random, 15, 20));
                    addTotal(nextTotals, "AUROMANCY", between(random, 15, 20));
                }
                case "beacon" -> {
                    nextBonus = Math.incrementExact(nextBonus);
                    nextPenalty = Math.incrementExact(nextPenalty);
                }
                default -> {
                    TheoryCardEffects.Plan plan = TheoryCardEffects.plan(card, this, knowledge, player, random);
                    if (plan == null) return false;
                    nextTotals.clear(); nextTotals.putAll(plan.totals());
                    nextBlocked.clear(); nextBlocked.addAll(plan.blocked());
                    nextBonus = plan.bonusDraws();
                    nextPenalty = plan.penaltyStart();
                    temporaryWarp = plan.temporaryWarp();
                    normalWarp = plan.normalWarp();
                    levelsSpent = plan.levelsSpent();
                    inspirationRefund = plan.inspirationRefund();
                    outputs = plan.outputs();
                }
            }
        } catch (ArithmeticException invalid) {
            return false;
        }

        // KnowledgeStore performs a checked raw debit, marks saved data dirty and syncs the player.
        if (debitCategory != null && !KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION,
                debitCategory, -KnowledgeType.OBSERVATION.units())) return false;
        boolean inventoryChanged = false;
        for (int slot = 0; slot < consumedItems.length; slot++) {
            if (consumedItems[slot] <= 0) continue;
            ItemStack stack = player.getInventory().items.get(slot);
            stack.shrink(consumedItems[slot]);
            if (stack.isEmpty()) player.getInventory().items.set(slot, ItemStack.EMPTY);
            inventoryChanged = true;
        }
        if (inventoryChanged) player.getInventory().setChanged();
        if (levelsSpent > 0) player.giveExperienceLevels(-levelsSpent);
        totals.clear();
        totals.putAll(nextTotals);
        blocked.clear();
        blocked.addAll(nextBlocked);
        bonusDraws = nextBonus;
        penaltyStart = nextPenalty;
        // Card refunds happen before the container charges its cost in BETA26.
        inspiration = Math.min(inspirationStart, Math.min(inspirationStart, inspiration + inspirationRefund) - card.cost());
        placedCards++;
        lastCard = card;
        choices.clear();
        randomSeed = random.nextLong();
        if (temporaryWarp > 0) KnowledgeStore.addTemporaryWarp(player, temporaryWarp);
        if (normalWarp > 0) KnowledgeStore.addNormalWarp(player, normalWarp);
        for (ItemStack output : outputs) {
            if (!player.getInventory().add(output) && !output.isEmpty()) player.drop(output, true);
        }
        return true;
    }

    private static int between(RandomSource random, int minimum, int maximum) {
        return maximum <= minimum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
    }

    private static void addTotal(Map<String, Integer> target, String category, int amount) {
        int next = Math.addExact(target.getOrDefault(category, 0), amount);
        if (next <= 0) target.remove(category);
        else target.put(category, next);
    }

    /** Pure preview; only the table may deliver these raw units and clear the session. */
    public Map<String, Integer> rewards() {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> sorted = totals.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed()).toList();
        int position = 0;
        for (var entry : sorted) {
            int raw = Math.round(entry.getValue() / 100.0f * KnowledgeType.THEORY.units());
            if (position > penaltyStart) raw = (int) Math.max(1.0, raw * 0.666666667);
            result.put(entry.getKey(), raw);
            position++;
        }
        return Collections.unmodifiableMap(result);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Owner", owner);
        tag.putInt("Inspiration", inspiration);
        tag.putInt("InspirationStart", inspirationStart);
        tag.putInt("BonusDraws", bonusDraws);
        tag.putInt("PenaltyStart", penaltyStart);
        tag.putInt("PlacedCards", placedCards);
        tag.putLong("RandomSeed", randomSeed);
        tag.put("Aids", writeStrings(aids));
        tag.put("AidCards", writeStrings(aidCards));
        tag.put("Blocked", writeStrings(blocked));
        CompoundTag categoryTotals = new CompoundTag();
        totals.forEach(categoryTotals::putInt);
        tag.put("Totals", categoryTotals);
        ListTag cards = new ListTag();
        choices.forEach(card -> cards.add(card.save()));
        tag.put("Choices", cards);
        if (lastCard != null) tag.put("LastCard", lastCard.save());
        return tag;
    }

    /** Loading restores initialized cards verbatim and never consults player state or RNG. */
    public static TheorySession load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Owner") || tag.getInt("Version") != 1) return null;
        TheorySession session = new TheorySession(tag.getUUID("Owner"));
        session.inspirationStart = tag.getInt("InspirationStart");
        session.inspiration = tag.getInt("Inspiration");
        session.bonusDraws = tag.getInt("BonusDraws");
        session.penaltyStart = tag.getInt("PenaltyStart");
        session.placedCards = tag.getInt("PlacedCards");
        session.randomSeed = tag.getLong("RandomSeed");
        if (session.inspirationStart < 1 || session.inspirationStart > 15 || session.inspiration < 0
                || session.inspiration > session.inspirationStart || session.bonusDraws < 0
                || session.penaltyStart < 0 || session.placedCards < 0) return null;
        ListTag aids = tag.getList("Aids", Tag.TAG_STRING);
        if (aids.size() > TheoryAids.keys().size()) return null;
        for (int i = 0; i < aids.size(); i++) {
            String aid = aids.getString(i);
            if (!TheoryAids.contains(aid) || !session.aids.add(aid)) return null;
        }
        List<String> remainingPool = new ArrayList<>();
        session.aids.forEach(aid -> remainingPool.addAll(TheoryAids.cards(aid)));
        ListTag aidCards = tag.getList("AidCards", Tag.TAG_STRING);
        for (int i = 0; i < aidCards.size(); i++) {
            String id = aidCards.getString(i);
            if (!remainingPool.remove(id)) return null;
            session.aidCards.add(id);
        }
        ListTag blocked = tag.getList("Blocked", Tag.TAG_STRING);
        for (int i = 0; i < blocked.size(); i++) {
            String category = blocked.getString(i);
            if (!ResearchCategories.contains(category)) return null;
            session.blocked.add(category);
        }
        CompoundTag totals = tag.getCompound("Totals");
        for (String category : totals.getAllKeys()) {
            if (!ResearchCategories.contains(category) || !totals.contains(category, Tag.TAG_INT)
                    || totals.getInt(category) <= 0) return null;
            session.totals.put(category, totals.getInt(category));
        }
        ListTag choices = tag.getList("Choices", Tag.TAG_COMPOUND);
        if (choices.size() > 3) return null;
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < choices.size(); i++) {
            TheoryCard card = TheoryCard.load(choices.getCompound(i));
            if (card == null || !ids.add(card.id())) return null;
            session.choices.add(card);
        }
        if (tag.contains("LastCard", Tag.TAG_COMPOUND)) {
            session.lastCard = TheoryCard.load(tag.getCompound("LastCard"));
            if (session.lastCard == null) return null;
        }
        return session;
    }

    private static ListTag writeStrings(Iterable<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }
}
