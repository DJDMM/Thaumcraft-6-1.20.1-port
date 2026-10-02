package thaumcraft.alchemy;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import thaumcraft.api.aspects.*;
import thaumcraft.research.KnowledgeStore;
import java.util.*;

/** Server-side datapack recipes; recipe discovery never grants research. */
public final class CrucibleRecipes extends SimpleJsonResourceReloadListener {
    private static volatile List<Entry> entries = List.of();
    public record Entry(ResourceLocation id, String research, Ingredient catalyst, ItemStack output, AspectList cost) {
        public boolean matches(ItemStack stack, AspectList available, ServerPlayer player) {
            if (!catalyst.test(stack) || player == null || !KnowledgeStore.get(player).knowsResearch(research)) return false;
            return hasAspects(available);
        }
        public boolean hasAspects(AspectList available) {
            for (Aspect aspect : cost.getAspects()) if (available.getAmount(aspect) < cost.getAmount(aspect)) return false;
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
            if (output.isEmpty() || output.getCount() > output.getMaxStackSize()) throw new JsonParseException("Invalid result: " + entry.getKey());
            updated.add(new Entry(entry.getKey(), GsonHelper.getAsString(data, "research"), Ingredient.fromJson(data.get("catalyst")), output, cost));
        });
        entries = List.copyOf(updated);
    }
    public static List<Entry> all() { return entries; }
    public static Entry find(ItemStack stack, AspectList available, ServerPlayer player) {
        for (Entry entry : entries) if (entry.matches(stack, available, player)) return entry;
        return null;
    }
}
