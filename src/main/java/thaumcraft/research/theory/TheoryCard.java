package thaumcraft.research.theory;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.equipment.items.CurioItem;
import thaumcraft.scanning.AspectRegistry;
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

/** Immutable, initialized view of all 33 registered TC6 BETA26 cards. */
public final class TheoryCard {
    private static final List<String> IDS = List.of("study", "analyze", "balance", "notation",
            "ponder", "rethink", "reject", "experimentation", "curio", "inspired", "enchantment", "beacon",
            "celestial", "concentrate", "reactions", "synthesis", "calibrate", "mind_over_matter", "tinker",
            "measure", "channel", "infuse", "focus", "awareness", "spellbinding", "sculpting", "scripting",
            "synergy", "dark_whispers", "glyphs", "portal", "revelation", "realization");
    private static final List<String> DEVICE_OPTIONS = List.of("thaumcraft:vis_resonator", "thaumcraft:thaumometer",
            "minecraft:anvil", "minecraft:activator_rail", "minecraft:dispenser", "minecraft:dropper",
            "minecraft:enchanting_table", "minecraft:ender_chest", "minecraft:jukebox", "minecraft:daylight_detector",
            "minecraft:piston", "minecraft:hopper", "minecraft:sticky_piston", "minecraft:map", "minecraft:compass",
            "minecraft:tnt_minecart", "minecraft:comparator", "minecraft:clock");
    private static final List<String> INFUSE_OPTIONS = List.of("thaumcraft:alumentum", "thaumcraft:nitor",
            "thaumcraft:amber", "thaumcraft:brain", "thaumcraft:fabric", "thaumcraft:salis_mundus",
            "thaumcraft:ingot_thaumium", "thaumcraft:ingot_brass", "thaumcraft:quicksilver", "minecraft:gold_ingot",
            "minecraft:iron_ingot", "minecraft:diamond", "minecraft:emerald", "minecraft:blaze_rod", "minecraft:leather",
            "minecraft:white_wool", "minecraft:brick", "minecraft:arrow", "minecraft:egg", "minecraft:feather",
            "minecraft:glowstone_dust", "minecraft:redstone", "minecraft:ghast_tear", "minecraft:gunpowder",
            "minecraft:bow", "minecraft:golden_sword", "minecraft:iron_sword", "minecraft:iron_pickaxe",
            "minecraft:golden_pickaxe", "minecraft:quartz", "minecraft:apple");

    private final String id;
    private final long seed;
    private final boolean fromAid;
    private final String category;
    private final String sourceCategory;
    private final String targetCategory;
    private final int amount;
    private final int md1;
    private final int md2;
    private final CompoundTag parameters = new CompoundTag();

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
            case "study", "balance", "notation", "celestial", "enchantment", "curio", "concentrate", "reactions",
                    "synthesis", "calibrate", "mind_over_matter", "tinker", "measure", "channel", "infuse",
                    "focus", "awareness", "spellbinding", "sculpting", "scripting", "synergy", "dark_whispers",
                    "glyphs", "revelation", "realization" -> 1;
            case "beacon" -> -2;
            case "rethink", "portal" -> -1;
            case "reject" -> 0;
            default -> 2;
        };
    }

    public boolean aidOnly() { return id.equals("study") || id.equals("notation")
            || id.equals("enchantment") || id.equals("beacon") || id.equals("dark_whispers")
            || id.equals("glyphs") || id.equals("portal"); }

    /** The accessor also copies, so callers cannot mutate a persisted card's requirement view. */
    public record RequiredItem(ItemStack stack, boolean consumed) {
        public RequiredItem { stack = Objects.requireNonNull(stack, "stack").copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public List<RequiredItem> requiredItems() {
        return switch (id) {
            case "celestial" -> List.of(new RequiredItem(CelestialModule.note(md1), true),
                    new RequiredItem(CelestialModule.note(md2), true));
            case "curio", "mind_over_matter" -> List.of(new RequiredItem(parameterStack(), true));
            case "tinker" -> List.of(new RequiredItem(parameterStack(), false));
            case "concentrate" -> List.of(new RequiredItem(AspectCrystalItem.create(aspect("aspect1")), false));
            case "reactions" -> List.of(new RequiredItem(AspectCrystalItem.create(aspect("aspect1")), false),
                    new RequiredItem(AspectCrystalItem.create(aspect("aspect2")), false));
            case "synthesis" -> List.of(new RequiredItem(AspectCrystalItem.create(aspect("aspect1")), true),
                    new RequiredItem(AspectCrystalItem.create(aspect("aspect2")), true));
            case "channel" -> List.of(new RequiredItem(CatalogModule.aspectStack("phial_filled", aspect("aspect1"), 10), false));
            case "infuse" -> List.of(new RequiredItem(parameterStack(), true),
                    new RequiredItem(CatalogModule.aspectStack("phial_filled", aspect("aspect1"), 10), true));
            case "sculpting" -> List.of(new RequiredItem(new ItemStack(Items.CLAY_BALL), true));
            default -> List.of();
        };
    }

    public int tableInkExtra() { return id.equals("scripting") ? 1 : 0; }
    public int tablePaperExtra() { return id.equals("scripting") ? 1 : 0; }
    public Aspect aspect(String key) { return Aspect.getAspect(parameters.getString(key)); }
    public ItemStack parameterStack() { return ItemStack.of(parameters.getCompound("stack")); }
    public int value() { return parameters.getInt("value"); }
    public String curioType() {
        var key = ForgeRegistries.ITEMS.getKey(parameterStack().getItem());
        return key == null ? "" : key.getPath().replace("curio_", "");
    }

    public int requiredLevels() { return id.equals("enchantment") ? 5 : id.equals("spellbinding") ? 1 : 0; }
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
                if (!matchesRequirement(found, wanted)) continue;
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

    /** BETA26 InventoryUtils uses exact damage and a relaxed, found-to-template tag comparison.
     * Unnamed templates ignore unrelated NBT; aspect-bearing templates require the complete Aspects entry.
     * Earlier-port primal IDs are normalized for compatibility, never accepting malformed crystals. */
    public static boolean matchesRequirement(ItemStack found, ItemStack wanted) {
        if (found.isEmpty() || wanted.isEmpty()) return false;
        if (oreEquivalent(found, wanted)) return true;
        boolean crystal = AspectCrystalItem.isCrystal(wanted);
        if (crystal && AspectCrystalItem.isCrystal(found) && found.getItem() != wanted.getItem()) {
            Aspect aspect = AspectCrystalItem.crystalAspect(found);
            found = AspectCrystalItem.create(aspect, found.getCount());
        }
        if (!found.is(wanted.getItem()) || found.getDamageValue() != wanted.getDamageValue()) return false;
        // Modern damage moved from item metadata into NBT. Strip only that storage key;
        // pristine named tools still match the original no-NBT option template.
        CompoundTag wantedTag = wanted.hasTag() ? wanted.getTag().copy() : new CompoundTag();
        CompoundTag foundTag = found.hasTag() ? found.getTag().copy() : new CompoundTag();
        wantedTag.remove("Damage"); foundTag.remove("Damage");
        if (wantedTag.isEmpty()) return true;
        if (foundTag.isEmpty()) return false;
        for (String key : foundTag.getAllKeys())
            if (!wantedTag.contains(key) || !Objects.equals(foundTag.get(key), wantedTag.get(key))) return false;
        // Empty/partial modern NBT must not impersonate the original canonical aspect template.
        return !crystal && !(wanted.getItem() instanceof IEssentiaContainerItem)
                || Objects.equals(foundTag.get("Aspects"), wantedTag.get("Aspects"));
    }

    /** Original payment enables OreDictionary before its damage/NBT fallback. Modern tags
     * preserve the relevant BETA26 groups instead of treating every forge:ingots item alike. */
    private static boolean oreEquivalent(ItemStack found, ItemStack wanted) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(wanted.getItem());
        if (key == null) return false;
        String tag = switch (key.toString()) {
            case "thaumcraft:ingot_thaumium" -> "forge:ingots/thaumium";
            case "thaumcraft:ingot_brass" -> "forge:ingots/brass";
            case "thaumcraft:amber" -> "forge:gems/amber";
            case "thaumcraft:quicksilver" -> "forge:quicksilver";
            case "minecraft:iron_ingot" -> "forge:ingots/iron";
            case "minecraft:gold_ingot" -> "forge:ingots/gold";
            case "minecraft:diamond" -> "forge:gems/diamond";
            case "minecraft:emerald" -> "forge:gems/emerald";
            case "minecraft:quartz" -> "forge:gems/quartz";
            case "minecraft:redstone" -> "forge:dusts/redstone";
            case "minecraft:glowstone_dust" -> "forge:dusts/glowstone";
            case "minecraft:gunpowder" -> "forge:gunpowder";
            case "minecraft:leather" -> "forge:leather";
            case "minecraft:feather" -> "forge:feathers";
            case "minecraft:egg" -> "forge:eggs";
            case "minecraft:brick" -> "forge:bricks/normal";
            case "minecraft:ender_chest" -> "forge:chests";
            case "minecraft:white_wool" -> "minecraft:wool";
            case "thaumcraft:nitor" -> "thaumcraft:nitor";
            default -> null;
        };
        if (tag == null) return false;
        if (found.is(wanted.getItem())) return true;
        if (wanted.is(Items.WHITE_WOOL)) return found.is(ItemTags.WOOL);
        if (wanted.is(Items.ENDER_CHEST) && (found.is(Items.CHEST) || found.is(Items.TRAPPED_CHEST))) return true;
        if (key.toString().equals("thaumcraft:nitor")) {
            ResourceLocation foundKey = ForgeRegistries.ITEMS.getKey(found.getItem());
            if (foundKey != null && foundKey.getNamespace().equals("thaumcraft")
                    && foundKey.getPath().startsWith("nitor_")) return true;
        }
        return found.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag)));
    }

    private String translationId() {
        return switch (id) { case "mind_over_matter" -> "mindmatter"; case "dark_whispers" -> "darkwhisper";
            case "glyphs" -> "glyph"; default -> id; };
    }

    public Component title() {
        return switch (id) {
            case "study", "analyze" -> Component.translatable("card." + id + ".name",
                    categoryName(category).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));
            case "reject" -> Component.translatable("card.reject.name",
                    categoryName(targetCategory).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));
            case "channel" -> Component.translatable("card.channel.name", Component.literal(aspect("aspect1").getName())
                    .withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));
            default -> Component.translatable("card." + translationId() + ".name");
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
            case "channel", "concentrate" -> Component.translatable("card." + id + ".text", aspect("aspect1").getName());
            case "reactions", "synthesis" -> Component.translatable("card." + id + ".text",
                    aspect("aspect1").getName(), aspect("aspect2").getName());
            case "infuse" -> Component.translatable("card.infuse.text", aspect("aspect1").getName(), parameterStack().getHoverName(), value());
            case "mind_over_matter" -> Component.translatable("card.mindmatter.text", value());
            case "tinker" -> Component.translatable("card.tinker.text", value(), value() + 10);
            default -> Component.translatable("card." + translationId() + ".text");
        };
    }

    private static net.minecraft.network.chat.MutableComponent categoryName(String category) {
        return Component.translatable("tc.research_category." + category).withStyle(ChatFormatting.BOLD);
    }

    String sourceCategory() { return sourceCategory; }
    String targetCategory() { return targetCategory; }
    int amount() { return amount; }

    public static TheoryCard initialize(String id, long seed, boolean fromAid, TheorySession session,
                                 PlayerKnowledge knowledge) {
        return initialize(id, seed, fromAid, session, knowledge, null, 0);
    }

    public static TheoryCard initialize(String id, long seed, boolean fromAid, TheorySession session,
                                 PlayerKnowledge knowledge, Inventory inventory, int levels) {
        if (!IDS.contains(id)) return null;
        TheoryCard card = new TheoryCard(id, seed, fromAid, fixedCategory(id), null, null, 0);
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
            case "curio": {
                if (inventory == null) return null;
                List<ItemStack> curios = inventory.items.stream().filter(stack -> !stack.isEmpty()
                        && stack.getItem() instanceof CurioItem).map(stack -> stack.copyWithCount(1)).toList();
                if (curios.isEmpty()) return null;
                card.parameters.put("stack", curios.get(random.nextInt(curios.size())).save(new CompoundTag()));
                return card;
            }
            case "spellbinding": return levels > 0 ? card : null;
            case "synergy": return session.totals().getOrDefault("ARTIFICE", 0)
                    + (long) session.totals().getOrDefault("ALCHEMY", 0)
                    + session.totals().getOrDefault("INFUSION", 0) >= 15 ? card : null;
            case "concentrate", "channel", "infuse", "reactions", "synthesis": {
                List<Aspect> compounds = Aspect.getCompoundAspects();
                Aspect first = compounds.get(random.nextInt(compounds.size()));
                if (id.equals("synthesis")) {
                    card.parameters.putString("aspect3", first.getTag());
                    card.parameters.putString("aspect1", first.getComponents()[0].getTag());
                    card.parameters.putString("aspect2", first.getComponents()[1].getTag());
                } else {
                    card.parameters.putString("aspect1", first.getTag());
                    if (id.equals("reactions")) {
                        Aspect second = first;
                        while (second == first) second = compounds.get(random.nextInt(compounds.size()));
                        card.parameters.putString("aspect2", second.getTag());
                    }
                    if (id.equals("infuse")) initializeStack(card, INFUSE_OPTIONS, random);
                }
                return card;
            }
            case "tinker", "mind_over_matter": initializeStack(card, DEVICE_OPTIONS, random); return card;
            default: return card;
        }
    }

    private static String fixedCategory(String id) {
        return switch (id) {
            case "concentrate", "reactions", "synthesis" -> "ALCHEMY";
            case "calibrate", "mind_over_matter", "tinker" -> "ARTIFICE";
            case "measure", "channel", "infuse" -> "INFUSION";
            case "focus", "awareness", "spellbinding" -> "AUROMANCY";
            case "sculpting", "scripting", "synergy" -> "GOLEMANCY";
            case "dark_whispers", "glyphs", "portal", "revelation", "realization" -> "ELDRITCH";
            default -> null;
        };
    }

    private static void initializeStack(TheoryCard card, List<String> options, Random random) {
        var item = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(options.get(random.nextInt(options.size()))));
        ItemStack stack = new ItemStack(item);
        card.parameters.put("stack", stack.save(new CompoundTag()));
        double size = Math.sqrt(AspectRegistry.getAspects(stack).visSize());
        card.parameters.putInt("value", switch (card.id) {
            case "tinker" -> (int) size * 2;
            case "infuse" -> 10 + (int) (size * 1.5);
            default -> 10 + (int) size;
        });
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
        if (!parameters.isEmpty()) tag.put("Parameters", parameters.copy());
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
        if (fixedCategory(id) != null && !fixedCategory(id).equals(category)) return null;
        result.parameters.merge(tag.getCompound("Parameters").copy());
        if (!result.validParameters()) return null;
        return result;
    }

    private boolean validParameters() {
        switch (id) {
            case "concentrate", "channel", "infuse":
                if (aspect("aspect1") == null || aspect("aspect1").isPrimal()) return false;
                break;
            case "reactions":
                if (aspect("aspect1") == null || aspect("aspect2") == null || aspect("aspect1").isPrimal()
                        || aspect("aspect2").isPrimal() || aspect("aspect1") == aspect("aspect2")) return false;
                break;
            case "synthesis":
                Aspect result = aspect("aspect3");
                if (result == null || result.isPrimal() || result.getComponents()[0] != aspect("aspect1")
                        || result.getComponents()[1] != aspect("aspect2")) return false;
                break;
        }
        if (List.of("curio", "tinker", "mind_over_matter", "infuse").contains(id)) {
            ItemStack stack = parameterStack();
            if (stack.isEmpty() || stack.getCount() != 1 || stack.getDamageValue() != 0) return false;
            if (id.equals("curio")) return stack.getItem() instanceof CurioItem;
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            List<String> options = id.equals("infuse") ? INFUSE_OPTIONS : DEVICE_OPTIONS;
            if (key == null || !options.contains(key.toString()) || !parameters.contains("value", Tag.TAG_INT)
                    || value() < (id.equals("tinker") ? 0 : 10) || value() > 1000) return false;
        }
        return true;
    }

    private static String readCategory(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return ResearchCategories.contains(value) ? value : null;
    }
}
