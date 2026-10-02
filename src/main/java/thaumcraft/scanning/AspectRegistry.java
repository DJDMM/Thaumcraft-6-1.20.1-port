package thaumcraft.scanning;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Reloadable server aspect definitions. All results are detached copies, and amounts
 * are per item, never multiplied by the stack size. Exact IDs take priority over tags.
 * Data packs override a bundled file by its resource ID, or use a greater priority.
 */
public final class AspectRegistry {
    private static volatile Snapshot snapshot = Snapshot.empty();

    private AspectRegistry() {}

    public static AspectList getAspects(ItemStack stack) {
        if (stack.isEmpty()) return new AspectList();
        Snapshot data = snapshot;
        ResolutionBudget budget = new ResolutionBudget();
        AspectList result = data.resolve(stack, new HashSet<>(), 0, budget);
        // Never publish a partial answer if a pathological recipe graph exceeds its budget.
        return ItemAspectBonuses.apply(stack, budget.exhausted ? new AspectList() : result);
    }

    public static AspectList getAspects(BlockState state) {
        Snapshot data = snapshot;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        Rule rule = data.blocks.get(id);
        return rule != null ? rule.aspects.copy() : getAspects(new ItemStack(state.getBlock().asItem()));
    }

    public static AspectList getAspects(Entity entity) {
        if (entity instanceof ItemEntity item) return getAspects(item.getItem());
        Rule rule = snapshot.entities.get(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        AspectList result = rule == null ? new AspectList() : rule.aspects.copy();
        // This was an NBT entity variant in TC6. Modern Minecraft exposes a typed property.
        if (entity instanceof Creeper creeper && creeper.isPowered()) result.merge(Aspect.ENERGY, 15);
        return result;
    }

    public static int definitionCount() { return snapshot.definitionCount; }

    /** A per-query bound protects the server tick from exponential recipe graphs. */
    static final class ResolutionBudget {
        private int remaining = 4096;
        boolean exhausted;

        boolean spend() {
            if (remaining-- > 0) return true;
            exhausted = true;
            return false;
        }
    }

    private record Rule(String kind, List<ResourceLocation> targets, AspectList aspects,
                        boolean derive, ResourceLocation base, int priority, ResourceLocation source) {}

    static final class Snapshot {
        final Map<ResourceLocation, Rule> items = new HashMap<>();
        final Map<ResourceLocation, Rule> blocks = new HashMap<>();
        final Map<ResourceLocation, Rule> entities = new HashMap<>();
        final List<Rule> tags = new ArrayList<>();
        final Map<ResourceLocation, AspectList> resolved = new HashMap<>();
        final RecipeAspectResolver recipes;
        int definitionCount;

        Snapshot(RecipeManager manager, RegistryAccess access) {
            recipes = manager == null ? null : new RecipeAspectResolver(manager, access);
        }

        static Snapshot empty() { return new Snapshot(null, null); }

        AspectList resolve(ItemStack stack, HashSet<ResourceLocation> path, int depth, ResolutionBudget budget) {
            if (stack.isEmpty() || depth > 32 || !budget.spend()) return new AspectList();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (path.contains(id)) return new AspectList();
            // Cache only top-level resolutions: recursion can encounter an incomplete cycle.
            if (depth == 0 && resolved.containsKey(id)) return resolved.get(id).copy();
            Rule rule = items.get(id);
            if (rule == null) {
                for (Rule candidate : tags) {
                    if (candidate.targets.stream().anyMatch(tag -> stack.is(TagKey.create(Registries.ITEM, tag)))) {
                        rule = candidate;
                        break;
                    }
                }
            }
            path.add(id);
            AspectList result;
            if (rule != null && rule.base != null) {
                var baseItem = ForgeRegistries.ITEMS.getValue(rule.base);
                result = baseItem == null ? new AspectList() : resolve(new ItemStack(baseItem), path, depth + 1, budget);
                result.add(rule.aspects);
            } else if (rule != null && !rule.derive) {
                result = rule.aspects.copy();
            } else {
                result = recipes == null ? new AspectList() : recipes.derive(stack, this, path, depth + 1, budget);
                if (rule != null) result.add(rule.aspects);
            }
            path.remove(id);
            for (Aspect aspect : result.getAspects()) {
                int amount = result.getAmount(aspect);
                if (amount <= 0) result.remove(aspect);
                else if (amount > 500) { result.remove(aspect); result.add(aspect, 500); }
            }
            if (depth == 0 && !budget.exhausted) resolved.put(id, result.copy());
            return result;
        }
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        private final RecipeManager manager;
        private final RegistryAccess access;

        public Loader(RecipeManager manager, RegistryAccess access) {
            super(new Gson(), "aspects");
            this.manager = manager;
            this.access = access;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
            Snapshot next = new Snapshot(manager, access);
            List<Rule> rules = new ArrayList<>();
            for (var entry : resources.entrySet()) {
                JsonObject object = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                String kind = GsonHelper.getAsString(object, "type");
                if (!List.of("item", "item_tag", "block", "entity").contains(kind)) {
                    throw new JsonParseException("Unknown aspect definition type " + kind + " in " + entry.getKey());
                }
                List<ResourceLocation> targets = new ArrayList<>();
                for (JsonElement target : GsonHelper.getAsJsonArray(object, "targets")) {
                    ResourceLocation id = ResourceLocation.tryParse(target.getAsString());
                    if (id == null) throw new JsonParseException("Invalid target in " + entry.getKey());
                    targets.add(id);
                }
                if (targets.isEmpty()) throw new JsonParseException("Empty targets in " + entry.getKey());
                AspectList aspects = new AspectList();
                for (var aspectEntry : GsonHelper.getAsJsonObject(object, "aspects").entrySet()) {
                    Aspect aspect = Aspect.getAspect(aspectEntry.getKey());
                    double amount = aspectEntry.getValue().getAsDouble();
                    if (aspect == null || !Double.isFinite(amount) || amount < 1 || amount > 500 || amount != Math.floor(amount)) {
                        throw new JsonParseException("Unknown aspect or invalid integer amount in " + entry.getKey() + ": " + aspectEntry.getKey());
                    }
                    aspects.add(aspect, (int) amount);
                }
                ResourceLocation base = object.has("base") ? ResourceLocation.tryParse(GsonHelper.getAsString(object, "base")) : null;
                if (object.has("base") && base == null) throw new JsonParseException("Invalid base in " + entry.getKey());
                rules.add(new Rule(kind, List.copyOf(targets), aspects, GsonHelper.getAsBoolean(object, "derive", false),
                        base, GsonHelper.getAsInt(object, "priority", 0), entry.getKey()));
            }
            rules.sort(Comparator.comparingInt(Rule::priority).thenComparing(rule -> rule.source.toString()));
            for (Rule rule : rules) {
                Map<ResourceLocation, Rule> destination = switch (rule.kind) {
                    case "item" -> next.items;
                    case "block" -> next.blocks;
                    case "entity" -> next.entities;
                    default -> null;
                };
                if (destination == null) next.tags.add(0, rule);
                else for (ResourceLocation target : rule.targets) destination.put(target, rule);
            }
            next.definitionCount = rules.size();
            // Publish only a fully parsed snapshot. A malformed reload cannot partly mutate the registry.
            snapshot = next;
        }
    }
}
