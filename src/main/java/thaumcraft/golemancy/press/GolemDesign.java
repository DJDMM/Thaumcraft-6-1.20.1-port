package thaumcraft.golemancy.press;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchProgression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, bounded BETA26 golem property data. Part declarations are reference
 * data; manufacture additionally requires supported, completed canonical research.
 * No mutable ItemStack is kept in a definition or returned across calls.
 */
public final class GolemDesign {
    public static final long DEFAULT_PROPS = 0L;
    public static final int MAX_RANK = 10;

    /** Original ByteBuffer indices, most significant byte first. */
    public enum Category {
        MATERIAL(0, "material"), HEAD(1, "head"), ARMS(2, "arm"),
        LEGS(3, "leg"), ADDON(4, "addon");

        private final int byteIndex;
        private final String languageCategory;
        Category(int byteIndex, String languageCategory) {
            this.byteIndex = byteIndex;
            this.languageCategory = languageCategory;
        }
        public int byteIndex() { return byteIndex; }
    }

    /** Declaration order and opposite pairs are the original EnumGolemTrait. */
    public enum Trait {
        SMART, DEFT, CLUMSY, FIGHTER, WHEELED, FLYER, CLIMBER,
        HEAVY, LIGHT, FRAGILE, REPAIR, SCOUT, ARMORED, BRUTAL,
        FIREPROOF, BREAKER, HAULER, RANGED, BLASTPROOF;

        public Trait opposite() {
            return switch (this) {
                case DEFT -> CLUMSY;
                case CLUMSY -> DEFT;
                case HEAVY -> LIGHT;
                case LIGHT -> HEAVY;
                case FRAGILE -> ARMORED;
                case ARMORED -> FRAGILE;
                default -> null;
            };
        }
        public String nameKey() { return "golem.trait." + name().toLowerCase(Locale.ROOT); }
        public String descriptionKey() { return "golem.trait.text." + name().toLowerCase(Locale.ROOT); }
        public ResourceLocation icon() {
            return tc("textures/misc/golem/tag_" + name().toLowerCase(Locale.ROOT) + ".png");
        }
    }

    /** Public read-only selector data; only MATERIAL has material statistics. */
    public record Part(Category category, int id, String key, List<String> research,
                       List<Trait> traits, ResourceLocation icon, int itemColor,
                       int healthMod, int armor, int damage) {
        public Part {
            research = List.copyOf(research);
            traits = List.copyOf(traits);
        }
        public String nameKey() {
            return "golem." + category.languageCategory + "." + key.toLowerCase(Locale.ROOT);
        }
        public String descriptionKey() {
            return "golem." + category.languageCategory + ".text." + key.toLowerCase(Locale.ROOT);
        }
        public boolean available(PlayerKnowledge knowledge) {
            return knowledge != null && research.stream().allMatch(raw ->
                    ResearchProgression.supportsProgression(raw) && ResearchProgression.isComplete(knowledge, raw));
        }
    }

    // 'base' and 'mech' refer to the selected material. Other tokens are exact
    // flattened catalogue metadata forms; counts belong to that original stack.
    private record Component(String item, int count) {}
    private record Definition(Part part, List<Component> components, String base, String mechanism) {
        private Definition { components = List.copyOf(components); }
    }

    private static final List<Definition> MATERIALS = List.of(
            material(0, "WOOD", "MATSTUDWOOD", 5059370, 6, 2, 1, "thaumcraft:plank_greatwood", Trait.LIGHT),
            material(1, "IRON", "MATSTUDIRON", 16777215, 20, 8, 3, "thaumcraft:plate_iron", Trait.HEAVY, Trait.FIREPROOF, Trait.BLASTPROOF),
            material(2, "CLAY", "MATSTUDCLAY", 13071447, 10, 4, 2, "minecraft:terracotta", Trait.FIREPROOF),
            material(3, "BRASS", "MATSTUDBRASS", 15638812, 16, 6, 3, "thaumcraft:plate_brass", Trait.LIGHT),
            material(4, "THAUMIUM", "MATSTUDTHAUMIUM", 5257074, 24, 10, 4, "thaumcraft:plate_thaumium", Trait.HEAVY, Trait.FIREPROOF, Trait.BLASTPROOF),
            material(5, "VOID", "MATSTUDVOID", 1445161, 20, 6, 4, "thaumcraft:plate_void", Trait.REPAIR));

    private static final List<Definition> HEADS = List.of(
            part(Category.HEAD, 0, "BASIC", "head_basic", List.of("MINDCLOCKWORK"), List.of(c("thaumcraft:mind_clockwork", 1))),
            part(Category.HEAD, 1, "SMART", "head_smart", List.of("MINDBIOTHAUMIC"), List.of(c("thaumcraft:mind_biothaumic", 1)), Trait.SMART, Trait.FRAGILE),
            part(Category.HEAD, 2, "SMART_ARMORED", "head_smartarmor", List.of("MINDBIOTHAUMIC", "GOLEMCOMBATADV"),
                    List.of(c("thaumcraft:mind_biothaumic", 1), c("thaumcraft:plate_brass", 1), c("base", 1), c("minecraft:white_wool", 1)), Trait.SMART),
            part(Category.HEAD, 3, "SCOUT", "head_scout", List.of("GOLEMVISION"),
                    List.of(c("thaumcraft:mind_clockwork", 1), c("thaumcraft:module_vision", 1)), Trait.SCOUT, Trait.FRAGILE),
            part(Category.HEAD, 4, "SMART_SCOUT", "head_smartscout", List.of("GOLEMVISION", "MINDBIOTHAUMIC"),
                    List.of(c("thaumcraft:mind_biothaumic", 1), c("thaumcraft:module_vision", 1)), Trait.SCOUT, Trait.SMART, Trait.FRAGILE));

    private static final List<Definition> ARMS = List.of(
            part(Category.ARMS, 0, "BASIC", "arms_basic", List.of("MINDCLOCKWORK"), List.of()),
            part(Category.ARMS, 1, "FINE", "arms_fine", List.of("MATSTUDBRASS"),
                    List.of(c("thaumcraft:mechanism_simple", 1), c("base", 1)), Trait.DEFT, Trait.FRAGILE),
            part(Category.ARMS, 2, "CLAWS", "arms_claws", List.of("GOLEMCOMBATADV"),
                    List.of(c("thaumcraft:module_aggression", 1), c("minecraft:shears", 2), c("base", 1)), Trait.FIGHTER, Trait.CLUMSY, Trait.BRUTAL),
            part(Category.ARMS, 3, "BREAKERS", "arms_breakers", List.of("GOLEMBREAKER"),
                    List.of(c("minecraft:diamond", 2), c("base", 1), c("minecraft:piston", 2)), Trait.BREAKER, Trait.CLUMSY, Trait.BRUTAL),
            part(Category.ARMS, 4, "DARTS", "arms_darts", List.of("GOLEMCOMBATADV"),
                    List.of(c("thaumcraft:module_aggression", 1), c("minecraft:dispenser", 2), c("minecraft:arrow", 32), c("mech", 1)),
                    Trait.FIGHTER, Trait.CLUMSY, Trait.RANGED, Trait.FRAGILE));

    private static final List<Definition> LEGS = List.of(
            part(Category.LEGS, 0, "WALKER", "legs_walker", List.of("MINDCLOCKWORK"), List.of(c("base", 1), c("mech", 1))),
            part(Category.LEGS, 1, "ROLLER", "legs_roller", List.of("MINDCLOCKWORK"),
                    List.of(c("minecraft:bowl", 2), c("minecraft:leather", 1), c("mech", 1)), Trait.WHEELED),
            part(Category.LEGS, 2, "CLIMBER", "legs_climber", List.of("GOLEMCLIMBER"),
                    List.of(c("minecraft:flint", 4), c("base", 1), c("mech", 1), c("mech", 1)), Trait.CLIMBER),
            part(Category.LEGS, 3, "FLYER", "legs_flyer", List.of("GOLEMFLYER"),
                    List.of(c("thaumcraft:levitator", 1), c("thaumcraft:plate_brass", 4), c("minecraft:slime_ball", 1), c("mech", 1)), Trait.FLYER, Trait.FRAGILE));

    private static final List<Definition> ADDONS = List.of(
            part(Category.ADDON, 0, "NONE", null, List.of("MINDCLOCKWORK"), List.of()),
            part(Category.ADDON, 1, "ARMORED", "addon_armored", List.of("GOLEMCOMBATADV"),
                    List.of(c("base", 1), c("base", 1), c("base", 1), c("base", 1)), Trait.ARMORED, Trait.HEAVY),
            part(Category.ADDON, 2, "FIGHTER", "addon_fighter", List.of("SEALGUARD"),
                    List.of(c("thaumcraft:module_aggression", 1), c("mech", 1)), Trait.FIGHTER),
            part(Category.ADDON, 3, "HAULER", "addon_hauler", List.of("MINDCLOCKWORK"),
                    List.of(c("minecraft:leather", 1), c("minecraft:chest", 1)), Trait.HAULER));

    private final long props;
    private final List<Definition> selected;
    private final Set<Trait> traits;
    private final Set<String> requiredResearch;

    private GolemDesign(long props) {
        this.props = props;
        this.selected = List.of(MATERIALS.get(byteAt(props, 0)), HEADS.get(byteAt(props, 1)),
                ARMS.get(byteAt(props, 2)), LEGS.get(byteAt(props, 3)), ADDONS.get(byteAt(props, 4)));
        EnumSet<Trait> combined = EnumSet.noneOf(Trait.class);
        LinkedHashSet<String> research = new LinkedHashSet<>();
        for (Definition definition : selected) {
            research.addAll(definition.part.research);
            for (Trait trait : definition.part.traits) {
                // Original addTraitSmart removes the old opposite WITHOUT adding
                // the new trait. This is cancellation, not last-trait-wins.
                if (trait.opposite() != null && combined.contains(trait.opposite())) combined.remove(trait.opposite());
                else combined.add(trait);
            }
        }
        this.traits = Collections.unmodifiableSet(combined);
        this.requiredResearch = Collections.unmodifiableSet(research);
    }

    /** Invalid IDs, signed-byte payloads, inflated ranks and reserved bytes fail closed. */
    public static Optional<GolemDesign> parse(long props) {
        if (byteAt(props, 0) >= MATERIALS.size() || byteAt(props, 1) >= HEADS.size()
                || byteAt(props, 2) >= ARMS.size() || byteAt(props, 3) >= LEGS.size()
                || byteAt(props, 4) >= ADDONS.size() || byteAt(props, 5) > MAX_RANK
                || byteAt(props, 6) != 0 || byteAt(props, 7) != 0) return Optional.empty();
        return Optional.of(new GolemDesign(props));
    }
    public static Optional<GolemDesign> design(long props) { return parse(props); }
    public static Optional<GolemDesign> fromLong(long props) { return parse(props); }
    public static Optional<GolemDesign> create(int material, int head, int arms, int legs, int addon) {
        if (material < 0 || material >= MATERIALS.size() || head < 0 || head >= HEADS.size()
                || arms < 0 || arms >= ARMS.size() || legs < 0 || legs >= LEGS.size()
                || addon < 0 || addon >= ADDONS.size()) return Optional.empty();
        return parse(((long)material << 56) | ((long)head << 48) | ((long)arms << 40)
                | ((long)legs << 32) | ((long)addon << 24));
    }
    public static int byteAt(long props, int index) {
        if (index < 0 || index > 7) throw new IllegalArgumentException("Invalid golem byte index " + index);
        return (int)((props >>> (56 - index * 8)) & 0xFFL);
    }

    public long props() { return props; }
    public long toLong() { return props; }
    public int rank() { return byteAt(props, 5); }
    public Part material() { return selected.get(0).part; }
    public Part head() { return selected.get(1).part; }
    public Part arms() { return selected.get(2).part; }
    public Part legs() { return selected.get(3).part; }
    public Part addon() { return selected.get(4).part; }
    public Part part(Category category) { return selected.get(category.byteIndex).part; }
    public Set<Trait> traits() { return traits; }
    public boolean hasTrait(Trait trait) { return traits.contains(trait); }
    public Set<String> requiredResearch() { return requiredResearch; }

    /** GUI and C2S must use the same canonical completion check; aliases are excluded. */
    public boolean canManufacture(PlayerKnowledge knowledge) {
        return rank() == 0 && selected.stream().allMatch(definition -> definition.part.available(knowledge));
    }
    public boolean researchMet(PlayerKnowledge knowledge) { return canManufacture(knowledge); }
    public boolean accessible(PlayerKnowledge knowledge) { return canManufacture(knowledge); }
    public boolean accessible(ServerPlayer player) {
        return player != null && canManufacture(KnowledgeStore.of(player.serverLevel()).get(player.getUUID()));
    }
    public static List<Part> choices(Category category) {
        return definitions(category).stream().map(Definition::part).toList();
    }
    public static List<Part> choices(Category category, PlayerKnowledge knowledge) {
        return choices(category).stream().filter(part -> part.available(knowledge)).toList();
    }

    /** Original base*2 + mech, then ARMS, LEGS, HEAD, ADDON; merges exact item and tags. */
    public List<ItemStack> components() {
        ArrayList<ItemStack> result = new ArrayList<>();
        Definition material = selected.get(0);
        add(result, stack(material.base, 2));
        add(result, stack(material.mechanism, 1));
        for (int category : new int[]{2, 3, 1, 4}) {
            for (Component component : selected.get(category).components) {
                String id = switch (component.item) {
                    case "base" -> material.base;
                    case "mech" -> material.mechanism;
                    default -> component.item;
                };
                add(result, stack(id, component.count));
            }
        }
        return List.copyOf(result);
    }
    public List<ItemStack> generateComponents() { return components(); }
    public int essentiaCost() {
        return traits.size() * 2 + components().stream().mapToInt(ItemStack::getCount).sum();
    }

    /** Pinned bytecode multiplies BEFORE converting back to int (source decompiler differs). */
    public int health() {
        int health = 10 + material().healthMod;
        if (hasTrait(Trait.FRAGILE)) health = (int)(health * .75);
        return health + rank();
    }
    public int armor() {
        int armor = material().armor;
        if (hasTrait(Trait.ARMORED)) armor = (int)Math.max(armor * 1.5, armor + 1);
        if (hasTrait(Trait.FRAGILE)) armor = (int)(armor * .75);
        return armor;
    }
    public double attackDamage() {
        if (!hasTrait(Trait.FIGHTER)) return 0;
        double damage = material().damage;
        if (hasTrait(Trait.BRUTAL)) damage = Math.max(damage * 1.5, damage + 1);
        return damage + rank() * .25;
    }
    public float moveSpeed() {
        return 1 + rank() * .025F + (hasTrait(Trait.LIGHT) ? .2F : 0)
                + (hasTrait(Trait.HEAVY) ? -.175F : 0) + (hasTrait(Trait.FLYER) ? -.33F : 0)
                + (hasTrait(Trait.WHEELED) ? .25F : 0);
    }

    private static List<Definition> definitions(Category category) {
        return switch (category) {
            case MATERIAL -> MATERIALS;
            case HEAD -> HEADS;
            case ARMS -> ARMS;
            case LEGS -> LEGS;
            case ADDON -> ADDONS;
        };
    }
    private static Definition material(int id, String key, String research, int color, int health,
                                       int armor, int damage, String base, Trait... traits) {
        Part part = new Part(Category.MATERIAL, id, key, List.of(research), List.of(traits),
                tc("textures/items/golem.png"), color, health, armor, damage);
        return new Definition(part, List.of(), base, "thaumcraft:mechanism_simple");
    }
    private static Definition part(Category category, int id, String key, String icon,
                                   List<String> research, List<Component> components, Trait... traits) {
        return new Definition(new Part(category, id, key, research, List.of(traits),
                tc(icon == null ? "textures/blocks/blank.png" : "textures/misc/golem/" + icon + ".png"),
                0xFFFFFF, 0, 0, 0), components, null, null);
    }
    private static Component c(String item, int count) { return new Component(item, count); }
    private static ResourceLocation tc(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
    private static ItemStack stack(String id, int count) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(Items.AIR);
        if (item == Items.AIR) throw new IllegalStateException("Missing audited golem component " + id);
        return new ItemStack(item, count);
    }
    private static void add(List<ItemStack> result, ItemStack incoming) {
        for (ItemStack existing : result) {
            if (ItemStack.isSameItemSameTags(existing, incoming)) {
                existing.grow(incoming.getCount());
                return;
            }
        }
        result.add(incoming.copy());
    }
}
