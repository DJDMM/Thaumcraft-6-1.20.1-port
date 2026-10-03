package thaumcraft.client.research;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.alchemy.SalisMundusRecipe;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.research.LegacyResearchItems;
import thaumcraft.research.PlayerKnowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only views of the current server recipes, including datapack overrides. */
final class BookRecipeViews {
    record View(ResourceLocation id, String kind, List<Ingredient> ingredients, int width, int height,
                ItemStack output, String research, int vis, int[] crystals, AspectList aspects) {
        boolean unlocked(PlayerKnowledge knowledge) { return research.isEmpty() || knowledge.knowsResearch(research); }
    }
    private static List<View> crucible = List.of();
    private static final Map<String, String> OUTPUTS = Map.ofEntries(
            Map.entry("goggles", "goggles"), Map.entry("enchantedfabric", "fabric"),
            Map.entry("robechest", "cloth_chest"), Map.entry("robelegs", "cloth_legs"), Map.entry("robeboots", "cloth_boots"),
            Map.entry("thaumometer", "thaumometer"), Map.entry("tablewood", "table_wood"),
            Map.entry("inkwell", "scribing_tools"), Map.entry("salismundusfake", "salis_mundus"),
            Map.entry("phial", "phial_empty"), Map.entry("brickarcane", "stone_arcane_brick"),
            Map.entry("essentiasmelter", "smelter_basic"), Map.entry("essentiasmelterthaumium", "smelter_thaumium"),
            Map.entry("essentiasmeltervoid", "smelter_void"), Map.entry("wardedjar", "jar_normal"),
            Map.entry("jarvoid", "jar_void"), Map.entry("brassbrace", "jar_brace"), Map.entry("jarlabel", "label_blank"),
            Map.entry("jarlabelessence", "label_filled"), Map.entry("tubefilter", "tube_filter"),
            Map.entry("tuberestrict", "tube_restrict"), Map.entry("tubeoneway", "tube_oneway"),
            Map.entry("tubevalve", "tube_valve"), Map.entry("tubebuffer", "tube_buffer"),
            Map.entry("alchemicalconstruct", "metal_alchemical"), Map.entry("advalchemyconstruct", "metal_alchemical_advanced"),
            Map.entry("smelteraux", "smelter_aux"), Map.entry("smeltervent", "smelter_vent"));

    private BookRecipeViews() {}

    static void receive(CompoundTag snapshot) {
        if (!snapshot.contains("CrucibleRecipePreviews", Tag.TAG_LIST)) return;
        var rows = snapshot.getList("CrucibleRecipePreviews", Tag.TAG_COMPOUND);
        List<View> loaded = new ArrayList<>();
        for (int i = 0; i < Math.min(512, rows.size()); i++) {
            CompoundTag row = rows.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("Id"));
            ItemStack output = ItemStack.of(row.getCompound("Output"));
            if (id == null || output.isEmpty()) continue;
            try {
                Ingredient catalyst = Ingredient.fromJson(JsonParser.parseString(row.getString("Catalyst")));
                AspectList aspects = new AspectList();
                aspects.readFromNBT(row);
                loaded.add(new View(id, "crucible", List.of(catalyst), 1, 1, output,
                        row.getString("Research"), 0, new int[6], aspects));
            } catch (RuntimeException ignored) { /* Invalid display data never becomes a crafting operation. */ }
        }
        crucible = List.copyOf(loaded);
    }

    static List<View> resolve(String original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        String path = original.substring(original.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        Map<ResourceLocation, View> result = new LinkedHashMap<>();
        for (View view : crucible) {
            String current = view.id.getPath();
            if (current.equals(path) || path.equals("brassingot") && current.equals("brass")
                    || path.equals("thaumiumingot") && current.equals("thaumium")
                    || path.equals("viscrystalgroup") && itemId(view.output).equals("crystal_essence")) result.put(view.id, view);
        }
        for (Recipe<?> recipe : mc.level.getRecipeManager().getRecipes()) {
            if (path.equals("jarlabelessence") && recipe instanceof thaumcraft.essentia.EssentiaLabelRecipe) {
                // One representative aspect for the actual NBT-aware label recipe.
                // Its full phial remainder is supplied by the recipe, not consumed.
                var label = thaumcraft.catalog.CatalogModule.stack("label_blank");
                var phial = thaumcraft.catalog.CatalogModule.aspectStack("phial_filled", Aspect.FIRE, 10);
                result.put(recipe.getId(), new View(recipe.getId(), "crafting", List.of(Ingredient.of(label), Ingredient.of(phial)),
                        2, 1, thaumcraft.catalog.CatalogModule.aspectStack("label_filled", Aspect.FIRE, 1),
                        "", 0, new int[6], new AspectList()));
                continue;
            }
            if ((path.equals("salismundusfake") || path.equals("salis_mundus")) && recipe instanceof SalisMundusRecipe) {
                // CustomRecipe intentionally has no static result/ingredient list. This representative
                // view exists only while the actual synchronized custom recipe is present.
                // A compound crystal is deliberately included: BETA26 does not require primals here.
                List<Ingredient> inputs = List.of(Ingredient.of(Items.FLINT), Ingredient.of(Items.BOWL), Ingredient.of(Items.REDSTONE),
                        Ingredient.of(AspectCrystalItem.create(Aspect.AIR)), Ingredient.of(AspectCrystalItem.create(Aspect.FIRE)),
                        Ingredient.of(AspectCrystalItem.create(Aspect.MAGIC)));
                result.put(recipe.getId(), new View(recipe.getId(), "salis", inputs, 3, 2,
                        new ItemStack(AlchemyModule.SALIS_MUNDUS.get()), "", 0, new int[6], new AspectList()));
                continue;
            }
            ItemStack output = recipe.getResultItem(mc.level.registryAccess());
            if (output.isEmpty()) continue;
            String outputId = itemId(output);
            boolean group = path.equals("thaumium_stuff") && (outputId.startsWith("thaumium_") || outputId.equals("plate_thaumium") || outputId.equals("metal_thaumium"))
                    || path.equals("brass_stuff") && (outputId.equals("plate_brass") || outputId.equals("metal_brass") || outputId.equals("nugget_brass"));
            String target = OUTPUTS.getOrDefault(path, path);
            if (!recipe.getId().getNamespace().equals("thaumcraft") || !(recipe.getId().getPath().equals(path) || outputId.equals(target) || group)) continue;
            if (recipe instanceof ArcaneRecipe arcane) {
                int[] crystals = new int[6];
                for (int i = 0; i < 6; i++) crystals[i] = arcane.crystalCost(i);
                int width = arcane.gridWidth(), height = arcane.gridHeight();
                result.put(recipe.getId(), new View(recipe.getId(), "arcane", List.copyOf(recipe.getIngredients()), width, height,
                        output.copy(), arcane.research(), arcane.vis(), crystals, new AspectList()));
            } else if (recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING) {
                int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, recipe.getIngredients().size());
                int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : Math.max(1, (recipe.getIngredients().size() + 2) / 3);
                result.put(recipe.getId(), new View(recipe.getId(), "crafting", List.copyOf(recipe.getIngredients()), width, height,
                        output.copy(), "", 0, new int[6], new AspectList()));
            }
        }
        return List.copyOf(result.values());
    }

    static String modernCraftId(String descriptor) {
        try { return LegacyResearchItems.resolve(descriptor).toString(); }
        catch (RuntimeException ignored) { return descriptor.split(";", 2)[0]; }
    }

    static ItemStack displayIngredient(Ingredient ingredient) {
        ItemStack[] choices = ingredient.getItems();
        return choices.length == 0 ? ItemStack.EMPTY : choices[(int)((System.currentTimeMillis() / 1200) % choices.length)];
    }
    static int previewCount() { return crucible.size(); }
    private static String itemId(ItemStack output) { return BuiltInRegistries.ITEM.getKey(output.getItem()).getPath(); }
}
