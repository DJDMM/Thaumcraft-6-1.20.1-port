package thaumcraft.alchemy;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.packs.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import thaumcraft.api.aspects.*;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.scanning.AspectRegistry;
import java.util.*;

/** Server-side datapack recipes; recipe discovery never grants research. */
public final class CrucibleRecipes extends SimpleJsonResourceReloadListener {
    private static volatile List<Entry> entries = List.of();
    /** BETA26 postAspects copies an item's aspects, then subtracts the catalyst for transformations. */
    public record AspectCost(ItemStack source, ItemStack subtract) {
        public AspectCost { source = source.copyWithCount(1); subtract = subtract.copyWithCount(1); }
        @Override public ItemStack source() { return source.copy(); }
        @Override public ItemStack subtract() { return subtract.copy(); }
        public AspectList resolve() {
            AspectList result = AspectRegistry.getAspects(source);
            if (!subtract.isEmpty()) result.remove(AspectRegistry.getAspects(subtract));
            // AspectList.remove deletes zero/negative entries; it never creates a negative payment.
            for (Aspect aspect : result.getAspects()) if (result.getAmount(aspect) <= 0) result.remove(aspect);
            return result;
        }
    }
    public record Entry(ResourceLocation id, String research, Ingredient catalyst, ItemStack output, AspectList cost,
                        CompoundTag catalystNbt, AspectCost aspectCost) {
        public Entry(ResourceLocation id, String research, Ingredient catalyst, ItemStack output, AspectList cost) {
            this(id, research, catalyst, output, cost, null, null);
        }
        public Entry(ResourceLocation id, String research, Ingredient catalyst, ItemStack output, AspectList cost, CompoundTag catalystNbt) {
            this(id, research, catalyst, output, cost, catalystNbt, null);
        }
        public Entry { catalystNbt = catalystNbt == null ? null : catalystNbt.copy(); }
        @Override public AspectList cost() { return aspectCost == null ? cost.copy() : aspectCost.resolve(); }
        @Override public CompoundTag catalystNbt() { return catalystNbt == null ? null : catalystNbt.copy(); }
        /** IngredientNBTTC checks complete top-level entries; extra unrelated root tags are allowed. */
        public boolean matchesCatalyst(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !catalyst.test(stack)) return false;
            if (catalystNbt == null || catalystNbt.isEmpty()) return true;
            CompoundTag actual = stack.getTag();
            if (actual == null) return false;
            for (String key : catalystNbt.getAllKeys())
                if (!Objects.equals(catalystNbt.get(key), actual.get(key))) return false;
            return true;
        }
        public boolean matches(ItemStack stack, AspectList available, ServerPlayer player) {
            if (!matchesCatalyst(stack) || player == null || !KnowledgeStore.get(player).knowsResearch(research)) return false;
            return hasAspects(available);
        }
        public boolean hasAspects(AspectList available) {
            AspectList required = cost();
            for (Aspect aspect : required.getAspects()) if (available.getAmount(aspect) < required.getAmount(aspect)) return false;
            return true;
        }
    }
    public CrucibleRecipes() { super(new Gson(), "crucible_recipes"); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager resources, ProfilerFiller profiler) {
        List<Entry> updated = new ArrayList<>();
        json.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject data = entry.getValue().getAsJsonObject();
            AspectList cost = new AspectList();
            for (var value : data.getAsJsonObject("aspects").entrySet()) {
                Aspect aspect = Aspect.getAspect(value.getKey());
                int amount = value.getValue().getAsInt();
                if (aspect == null || amount <= 0 || amount > 500) throw new JsonParseException("Invalid aspect in " + entry.getKey());
                cost.add(aspect, amount);
            }
            ItemStack output = ShapedRecipe.itemStackFromJson(data.getAsJsonObject("result"));
            // The original recipe output carries Aspects NBT, rather than being a generic crystal.
            JsonObject result = data.getAsJsonObject("result");
            if (result.has("aspect")) {
                Aspect aspect = Aspect.getAspect(GsonHelper.getAsString(result, "aspect"));
                if (aspect == null || !(output.getItem() instanceof AspectCrystalItem))
                    throw new JsonParseException("Invalid crystal output: " + entry.getKey());
                ((IEssentiaContainerItem) output.getItem()).setAspects(output, new AspectList().add(aspect, 1));
            }
            if (output.isEmpty() || output.getCount() > output.getMaxStackSize()) throw new JsonParseException("Invalid result: " + entry.getKey());
            CompoundTag catalystNbt = null;
            if (data.has("catalyst_nbt")) {
                try { catalystNbt = TagParser.parseTag(GsonHelper.getAsString(data, "catalyst_nbt")); }
                catch (Exception failure) { throw new JsonParseException("Invalid catalyst NBT: " + entry.getKey(), failure); }
            }
            AspectCost aspectCost = null;
            if (data.has("aspect_cost")) {
                JsonObject formula = GsonHelper.getAsJsonObject(data, "aspect_cost");
                ItemStack source = ShapedRecipe.itemStackFromJson(formula);
                ItemStack subtract = formula.has("subtract") ? ShapedRecipe.itemStackFromJson(formula.getAsJsonObject("subtract")) : ItemStack.EMPTY;
                if (source.isEmpty() || source.getCount() != 1 || (!subtract.isEmpty() && subtract.getCount() != 1))
                    throw new JsonParseException("Invalid dynamic aspect cost: " + entry.getKey());
                aspectCost = new AspectCost(source, subtract);
            }
            updated.add(new Entry(entry.getKey(), GsonHelper.getAsString(data, "research"), Ingredient.fromJson(data.get("catalyst")), output, cost, catalystNbt, aspectCost));
        });
        entries = List.copyOf(updated);
    }
    public static List<Entry> all() { return entries; }
    public static Entry find(ItemStack stack, AspectList available, ServerPlayer player) {
        Entry selected = null;
        int highest = 0;
        // BETA26 chooses the greatest total aspect cost, keeping the first equal-cost match.
        // Resource-ID order supplies a deterministic tie order in modern datapack reloads.
        for (Entry entry : entries) if (entry.matches(stack, available, player) && entry.cost().visSize() > highest) {
            selected = entry;
            highest = entry.cost().visSize();
        }
        return selected;
    }
}
