package thaumcraft.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pinned BETA26 book displays. These definitions never enter a recipe registry or grant research. */
public final class BookRecipeCatalog {
    public record Definition(ResourceLocation id, String kind, int width, int height, String research,
                             int vis, boolean shapeless, int instability, int xp, String note, JsonObject data) {
        public JsonObject data() { return data.deepCopy(); }
        /** Fresh instances protect the reference from UI or external inventory mutations. */
        public ItemStack output() { return stack(data.getAsJsonObject("output")); }
        public List<Ingredient> ingredients() {
            List<Ingredient> result = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray("ingredients")) result.add(ingredient(element.getAsJsonObject()));
            return List.copyOf(result);
        }
        public AspectList aspects() { return aspectList(data.getAsJsonObject("aspects")); }
        public int[] crystals() {
            Aspect[] primals = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY};
            int[] values = new int[6];
            JsonObject costs = data.getAsJsonObject("crystals");
            for (int i = 0; i < values.length; i++) if (costs.has(primals[i].getTag())) values[i] = costs.get(primals[i].getTag()).getAsInt();
            return values;
        }
    }
    private record Catalog(Map<String, Definition> recipes, Map<String, List<String>> groups, Map<String, String> status) {}
    private static final Catalog CATALOG = load();

    private BookRecipeCatalog() {}

    public static List<Definition> definitions(String original) {
        String key = normalize(original);
        Definition direct = CATALOG.recipes.get(key);
        if (direct != null) return List.of(direct);
        List<String> group = CATALOG.groups.get(key);
        if (group == null) return List.of();
        return group.stream().map(CATALOG.recipes::get).toList();
    }
    public static String originalStatus(String original) { return CATALOG.status.getOrDefault(normalize(original), "not_referenced"); }
    public static Set<String> referencedKeys() { return CATALOG.status.keySet(); }
    public static List<Definition> allDefinitions() { return List.copyOf(CATALOG.recipes.values()); }
    public static int recipeCount() { return CATALOG.recipes.size(); }
    private static String normalize(String raw) { return raw.toLowerCase(Locale.ROOT); }

    private static Catalog load() {
        try (var stream = BookRecipeCatalog.class.getResourceAsStream("/assets/thaumcraft/research/book_recipes.json")) {
            if (stream == null) throw new IllegalStateException("Missing BETA26 book recipe catalogue");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.get("reference_only").getAsBoolean() || root.get("schema").getAsInt() != 1)
                throw new IllegalStateException("Book recipes must remain read-only reference data");
            Map<String, Definition> recipes = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("recipes")) {
                JsonObject row = element.getAsJsonObject();
                ResourceLocation id = new ResourceLocation(row.get("id").getAsString());
                Definition definition = new Definition(id, row.get("kind").getAsString(), row.get("width").getAsInt(),
                        row.get("height").getAsInt(), row.get("research").getAsString(), row.get("vis").getAsInt(),
                        row.get("shapeless").getAsBoolean(), row.get("instability").getAsInt(), row.get("xp").getAsInt(),
                        row.has("note") ? row.get("note").getAsString() : "", row.deepCopy());
                if (recipes.put(id.toString(), definition) != null) throw new IllegalStateException("Duplicate reference recipe " + id);
            }
            Map<String, List<String>> groups = new LinkedHashMap<>();
            root.getAsJsonObject("groups").entrySet().forEach(entry -> {
                List<String> ids = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    String id = element.getAsString();
                    if (!recipes.containsKey(id)) throw new IllegalStateException("Missing reference group member " + id);
                    ids.add(id);
                }
                groups.put(entry.getKey(), List.copyOf(ids));
            });
            Map<String, String> status = new LinkedHashMap<>();
            root.getAsJsonObject("referenced_keys").entrySet().forEach(entry ->
                    status.put(entry.getKey(), entry.getValue().getAsJsonObject().get("status").getAsString()));
            return new Catalog(Collections.unmodifiableMap(recipes), Collections.unmodifiableMap(groups), Collections.unmodifiableMap(status));
        } catch (Exception failure) { throw new IllegalStateException("Invalid pinned BETA26 recipe catalogue", failure); }
    }

    private static ItemStack stack(JsonObject row) {
        ResourceLocation id = new ResourceLocation(row.get("item").getAsString());
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Unmapped book item " + id);
        ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(id), row.has("count") ? row.get("count").getAsInt() : 1);
        if (row.has("nbt")) {
            try { out.setTag(TagParser.parseTag(row.get("nbt").getAsString())); }
            catch (Exception failure) { throw new IllegalStateException("Invalid reference NBT for " + id, failure); }
        }
        return out;
    }
    private static Ingredient ingredient(JsonObject row) {
        if (row.size() == 0) return Ingredient.EMPTY;
        if (row.has("item")) return Ingredient.of(stack(row));
        if (row.has("alternatives")) {
            List<ItemStack> alternatives = new ArrayList<>();
            for (JsonElement element : row.getAsJsonArray("alternatives")) alternatives.add(stack(element.getAsJsonObject()));
            return Ingredient.of(alternatives.stream());
        }
        if (row.has("ore")) return oreIngredient(row.get("ore").getAsString());
        throw new IllegalStateException("Unknown reference ingredient " + row);
    }
    private static AspectList aspectList(JsonObject costs) {
        AspectList out = new AspectList();
        costs.entrySet().forEach(entry -> {
            Aspect aspect = Aspect.getAspect(entry.getKey());
            if (aspect == null) throw new IllegalStateException("Unknown reference aspect " + entry.getKey());
            out.add(aspect, entry.getValue().getAsInt());
        });
        return out;
    }
    /** Ore Dictionary to modern tags; fallbacks make pinned examples usable with an empty client tag cache. */
    private static Ingredient oreIngredient(String ore) {
        List<ItemStack> choices = new ArrayList<>();
        String tag = switch (ore) {
            case "blockGlass" -> "forge:glass";
            case "paneGlass" -> "forge:glass_panes";
            case "blockRedstone" -> "forge:storage_blocks/redstone";
            case "plankWood" -> "minecraft:planks";
            case "slabWood" -> "minecraft:wooden_slabs";
            case "stickWood" -> "forge:rods/wooden";
            case "trapdoorWood" -> "minecraft:wooden_trapdoors";
            case "slimeball" -> "forge:slimeballs";
            case "nitor" -> "thaumcraft:nitor";
            case "string", "leather", "cobblestone", "stone" -> "forge:" + ore;
            case "workbench" -> "forge:workbench";
            default -> {
                String[] prefixes = {"ingot", "nugget", "plate", "gem", "ore", "dust", "dye"};
                String converted = "";
                for (String prefix : prefixes) if (ore.startsWith(prefix)) {
                    String value = ore.substring(prefix.length()).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                    converted = "forge:" + (prefix.equals("dye") ? "dyes" : prefix + "s") + "/" + value;
                    break;
                }
                yield converted;
            }
        };
        if (!tag.isEmpty()) BuiltInRegistries.ITEM.getTag(TagKey.create(BuiltInRegistries.ITEM.key(), new ResourceLocation(tag)))
                .ifPresent(set -> set.forEach(holder -> choices.add(new ItemStack(holder.value()))));
        for (String fallback : oreFallbacks(ore)) {
            ResourceLocation id = new ResourceLocation(fallback);
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR && choices.stream().noneMatch(stack -> stack.is(item))) choices.add(new ItemStack(item));
        }
        return Ingredient.of(choices.stream());
    }
    private static List<String> oreFallbacks(String ore) {
        return switch (ore) {
            case "blockGlass" -> List.of("minecraft:glass");
            case "paneGlass" -> List.of("minecraft:glass_pane");
            case "blockRedstone" -> List.of("minecraft:redstone_block");
            case "plankWood" -> List.of("minecraft:oak_planks", "thaumcraft:plank_greatwood", "thaumcraft:plank_silverwood");
            case "slabWood" -> List.of("minecraft:oak_slab", "thaumcraft:slab_greatwood", "thaumcraft:slab_silverwood");
            case "stickWood" -> List.of("minecraft:stick");
            case "trapdoorWood" -> List.of("minecraft:oak_trapdoor");
            case "workbench" -> List.of("minecraft:crafting_table");
            case "slimeball" -> List.of("minecraft:slime_ball");
            case "cobblestone", "stone", "string", "leather" -> List.of("minecraft:"+ore);
            case "gemAmber" -> List.of("thaumcraft:amber");
            case "gemQuartz" -> List.of("minecraft:quartz");
            case "gemDiamond" -> List.of("minecraft:diamond");
            case "dustGlowstone" -> List.of("minecraft:glowstone_dust");
            case "dustRedstone" -> List.of("minecraft:redstone");
            case "nuggetMeat" -> List.of("thaumcraft:chunk_beef","thaumcraft:chunk_chicken","thaumcraft:chunk_pork","thaumcraft:chunk_fish","thaumcraft:chunk_rabbit","thaumcraft:chunk_mutton");
            case "nitor" -> allNitor();
            default -> {
                if (ore.startsWith("ingot")) {
                    String metal = ore.substring(5).toLowerCase(Locale.ROOT);
                    yield List.of(Set.of("iron","gold","copper").contains(metal) ? "minecraft:"+metal+"_ingot" : "thaumcraft:ingot_"+metal);
                }
                if (ore.startsWith("plate")) yield List.of("thaumcraft:plate_"+ore.substring(5).toLowerCase(Locale.ROOT));
                if (ore.startsWith("nugget")) {
                    String metal = ore.substring(6).toLowerCase(Locale.ROOT);
                    yield List.of(Set.of("iron","gold").contains(metal) ? "minecraft:"+metal+"_nugget" : "thaumcraft:nugget_"+metal);
                }
                if (ore.startsWith("ore")) {
                    String metal = ore.substring(3).toLowerCase(Locale.ROOT);
                    yield List.of(metal.equals("cinnabar") ? "thaumcraft:ore_cinnabar" : "minecraft:"+metal+"_ore");
                }
                if (ore.startsWith("dye")) {
                    String color = ore.substring(3).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                    // The BETA26 Ore Dictionary also accepted these non-dye-item vanilla materials.
                    String material = switch (color) { case "black" -> "ink_sac"; case "white" -> "bone_meal"; case "blue" -> "lapis_lazuli"; case "brown" -> "cocoa_beans"; default -> color+"_dye"; };
                    yield List.of("minecraft:"+material, "minecraft:"+color+"_dye");
                }
                throw new IllegalStateException("Unmapped reference Ore Dictionary name " + ore);
            }
        };
    }
    private static List<String> allNitor() {
        return List.of("thaumcraft:nitor_white","thaumcraft:nitor_orange","thaumcraft:nitor_magenta","thaumcraft:nitor_lightblue", "thaumcraft:nitor",
                "thaumcraft:nitor_lime","thaumcraft:nitor_pink","thaumcraft:nitor_gray","thaumcraft:nitor_silver","thaumcraft:nitor_cyan",
                "thaumcraft:nitor_purple","thaumcraft:nitor_blue","thaumcraft:nitor_brown","thaumcraft:nitor_green","thaumcraft:nitor_red","thaumcraft:nitor_black");
    }
}
