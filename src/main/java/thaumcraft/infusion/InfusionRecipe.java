package thaumcraft.infusion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolActions;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.IRechargable;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.research.PlayerKnowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Synchronized working infusion recipe; the book reference catalogue remains separate. */
public final class InfusionRecipe implements Recipe<Container> {
    public static final int MAX_RUNIC_INPUT_CHARGE = 26;
    private static final Set<String> BAUBLE_CLASSES = Set.of("ItemFocusPouch", "ItemVoidseerCharm", "ItemVerdantCharm",
            "ItemCuriosityBand", "ItemCloudRing", "ItemAmuletVis", "ItemCharmUndying", "ItemBaubles");
    private enum Mode { REPLACE, MUTATE, ENCHANTMENT, RUNIC }
    private final ResourceLocation id;
    private final JsonObject data;
    private final String research;
    private final Mode mode;
    private final InfusionIngredient central;
    private final List<InfusionIngredient> components;
    private final AspectList aspects;
    private final int instability;
    private final ItemStack result;
    private final ItemStack displayCentral;
    private final CompoundTag mutation;
    private final String mutationLabel;
    private final EnumInfusionEnchantment enchantment;

    private InfusionRecipe(ResourceLocation id, JsonObject data) {
        this.id = id; this.data = data.deepCopy();
        research = GsonHelper.getAsString(data, "research", "");
        if (research.length() > 256 || research.contains("&&") && research.contains("||"))
            throw new JsonSyntaxException("Invalid infusion research expression");
        try { mode = Mode.valueOf(GsonHelper.getAsString(data, "mode", "replace").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new JsonSyntaxException("Unknown infusion output mode", error); }
        central = data.has("central") ? InfusionIngredient.fromJson(GsonHelper.getAsJsonObject(data, "central")) : null;
        if (central == null && (mode == Mode.REPLACE || mode == Mode.MUTATE)) throw new JsonSyntaxException("Ordinary infusion needs central input");
        var array = GsonHelper.getAsJsonArray(data, "components");
        if (array.isEmpty() || array.size() > 128) throw new JsonSyntaxException("Infusion needs 1 to 128 components");
        if (mode == Mode.RUNIC && array.size() != 2) throw new JsonSyntaxException("Runic infusion needs base and augment components");
        List<InfusionIngredient> parsed = new ArrayList<>();
        array.forEach(element -> parsed.add(InfusionIngredient.fromJson(element.getAsJsonObject())));
        components = List.copyOf(parsed);
        aspects = new AspectList();
        var costs = GsonHelper.getAsJsonObject(data, "aspects", new JsonObject());
        for (String key : costs.keySet()) {
            Aspect aspect = Aspect.getAspect(key);
            int amount = GsonHelper.getAsInt(costs, key);
            if (aspect == null || amount <= 0 || amount > 1_000_000) throw new JsonSyntaxException("Invalid infusion aspect " + key);
            aspects.add(aspect, amount);
        }
        if (mode != Mode.RUNIC && aspects.size() == 0) throw new JsonSyntaxException("Infusion must have aspect costs");
        instability = GsonHelper.getAsInt(data, "instability", mode == Mode.ENCHANTMENT ? 4 : 0);
        if (instability < 0 || instability > 100) throw new JsonSyntaxException("Invalid infusion instability");
        result = mode == Mode.REPLACE ? readStack(GsonHelper.getAsJsonObject(data, "result")) : ItemStack.EMPTY;
        mutation = mode == Mode.MUTATE ? InfusionIngredient.parseNbt(GsonHelper.getAsString(data, "mutation")) : null;
        if (mutation != null && mutation.size() != 1) throw new JsonSyntaxException("Infusion mutation must contain one top-level tag");
        mutationLabel = mutation == null ? null : mutation.getAllKeys().iterator().next();
        if (mode == Mode.ENCHANTMENT) {
            try { enchantment = EnumInfusionEnchantment.valueOf(GsonHelper.getAsString(data, "enchantment").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new JsonSyntaxException("Unknown infusion enchantment", error); }
        } else enchantment = null;
        displayCentral = data.has("display_central") ? readStack(GsonHelper.getAsJsonObject(data, "display_central")) : ItemStack.EMPTY;
        if ((mode == Mode.ENCHANTMENT || mode == Mode.RUNIC) && displayCentral.isEmpty())
            throw new JsonSyntaxException("Dynamic infusion needs a separate display central example");
    }

    public record Plan(ResourceLocation id, ItemStack input, List<ItemStack> components, AspectList aspects,
                       int instability, ItemStack output, boolean preserveInput, String mutationLabel) {
        public Plan {
            input = input.copy(); components = components.stream().map(ItemStack::copy).toList();
            aspects = aspects.copy(); output = output.copy();
        }
        @Override public ItemStack input() { return input.copy(); }
        @Override public List<ItemStack> components() { return components.stream().map(ItemStack::copy).toList(); }
        @Override public AspectList aspects() { return aspects.copy(); }
        @Override public ItemStack output() { return output.copy(); }
    }

    public String research() { return research; }
    public String kind() { return mode == Mode.ENCHANTMENT ? "infusion_enchantment" : mode == Mode.RUNIC ? "runic" : "infusion"; }
    public boolean preservesInput() { return mode != Mode.REPLACE; }
    public String mutationLabel() { return mutationLabel; }
    public int instability() { return instability; }
    public AspectList aspects() { return aspects.copy(); }
    public ItemStack displayCentral() { return displayCentral.isEmpty() ? central.example() : displayCentral.copy(); }
    public List<InfusionIngredient> components(ItemStack input) {
        if (mode != Mode.RUNIC) return components;
        int charge = runicCharge(input);
        if (charge < 0 || charge > MAX_RUNIC_INPUT_CHARGE) return List.of();
        List<InfusionIngredient> out = new ArrayList<>();
        out.add(components.get(0));
        for (int i = 0; i <= charge; i++) out.add(components.get(1));
        return List.copyOf(out);
    }
    public AspectList aspects(ItemStack input) {
        if (mode == Mode.RUNIC) {
            int charge = runicCharge(input);
            if (charge < 0 || charge > MAX_RUNIC_INPUT_CHARGE) return new AspectList();
            int cost = 20 + (int)(20.0 * Math.pow(2.0, charge));
            return new AspectList().add(Aspect.PROTECT, cost).add(Aspect.CRYSTAL, cost / 2).add(Aspect.ENERGY, cost / 2);
        }
        if (mode != Mode.ENCHANTMENT) return aspects.copy();
        if (input == null || input.isEmpty()) return new AspectList();
        int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment) + 1;
        if (level < 1 || level > enchantment.maxLevel) return new AspectList();
        List<EnumInfusionEnchantment> existing = EnumInfusionEnchantment.getInfusionEnchantments(input);
        int other = existing.size() - (existing.contains(enchantment) ? 1 : 0);
        float multiplier = level + other * 0.33f;
        AspectList out = new AspectList();
        for (Aspect aspect : aspects.getAspects()) {
            float cost = aspects.getAmount(aspect) * multiplier;
            if (!Float.isFinite(cost) || cost >= Integer.MAX_VALUE) return new AspectList();
            out.add(aspect, (int)cost);
        }
        return out;
    }
    public int instability(ItemStack input) { return mode == Mode.RUNIC ? 5 + runicCharge(input) / 2 : instability; }
    public boolean unlocked(PlayerKnowledge knowledge) { return InfusionRecipes.known(knowledge, research); }

    public Optional<Plan> plan(PlayerKnowledge knowledge, ItemStack input, List<ItemStack> actual, RandomSource random) {
        if (knowledge == null || !unlocked(knowledge) || !matchesItems(input, actual)) return Optional.empty();
        AspectList cost = aspects(input);
        if (cost.size() == 0) return Optional.empty();
        ItemStack output = output(input, true, random);
        if (output.isEmpty()) return Optional.empty();
        return Optional.of(new Plan(id, input, actual, cost, instability(input), output, preservesInput(), mutationLabel));
    }
    public boolean matchesItems(ItemStack input, List<ItemStack> actual) {
        if (input == null || input.isEmpty() || actual == null) return false;
        if (central != null && !central.test(input)) return false;
        if (mode == Mode.ENCHANTMENT) {
            int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment);
            if (level < 0 || level >= enchantment.maxLevel || !eligibleEnchantment(input, enchantment.toolClasses)) return false;
        }
        if (mode == Mode.RUNIC && (!eligibleRunic(input) || runicCharge(input) < 0 || runicCharge(input) > MAX_RUNIC_INPUT_CHARGE)) return false;
        return InfusionRecipes.matchesComponents(components(input), actual);
    }
    private ItemStack output(ItemStack input, boolean rollWarp, RandomSource random) {
        if (mode == Mode.REPLACE) return result.copy();
        if (input == null || input.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = input.copy();
        if (mode == Mode.MUTATE) out.getOrCreateTag().put(mutationLabel, mutation.get(mutationLabel).copy());
        else if (mode == Mode.RUNIC) out.getOrCreateTag().putByte("TC.RUNIC", (byte)(runicCharge(input) + 1));
        else {
            int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment);
            if (level < 0 || level >= enchantment.maxLevel) return ItemStack.EMPTY;
            int existing = EnumInfusionEnchantment.getInfusionEnchantments(input).size();
            if (rollWarp && random.nextInt(10) < existing)
                out.getOrCreateTag().putByte("TC.WARP", (byte)(1 + (input.hasTag() ? input.getTag().getByte("TC.WARP") : 0)));
            EnumInfusionEnchantment.addInfusionEnchantment(out, enchantment, level + 1);
        }
        return out;
    }
    public ItemStack previewOutput(ItemStack input) { return output(input, false, null); }
    private static int runicCharge(ItemStack input) { return input == null || !input.hasTag() ? 0 : input.getTag().getByte("TC.RUNIC"); }
    public static boolean eligibleRunic(ItemStack input) {
        if (input == null || input.isEmpty()) return false;
        if (input.getItem() instanceof ArmorItem) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(input.getItem());
        return id.getNamespace().equals("thaumcraft") && CatalogModule.SPECS.stream()
                .anyMatch(spec -> spec.id().equals(id.getPath()) && BAUBLE_CLASSES.contains(spec.sourceClass()));
    }
    private static boolean eligibleEnchantment(ItemStack input, Set<String> classes) {
        if (classes.contains("all")) return true;
        if (classes.contains("weapon") && input.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) return true;
        if (input.getItem() instanceof DiggerItem) {
            if (classes.contains("axe") && input.canPerformAction(ToolActions.AXE_DIG)) return true;
            if (classes.contains("pickaxe") && input.canPerformAction(ToolActions.PICKAXE_DIG)) return true;
            if (classes.contains("shovel") && input.canPerformAction(ToolActions.SHOVEL_DIG)) return true;
        }
        if (input.getItem() instanceof ArmorItem armor) {
            String slot = switch (armor.getEquipmentSlot()) { case HEAD -> "helm"; case CHEST -> "chest"; case LEGS -> "legs"; case FEET -> "boots"; default -> "none"; };
            if (classes.contains("armor") || classes.contains(slot)) return true;
        }
        if (eligibleRunic(input) && !(input.getItem() instanceof ArmorItem) && classes.contains("bauble")) return true;
        return classes.contains("chargable") && input.getItem() instanceof IRechargable;
    }

    @Override public boolean matches(Container inventory, Level level) {
        if (inventory.getContainerSize() == 0) return false;
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) if (!inventory.getItem(slot).isEmpty()) inputs.add(inventory.getItem(slot));
        return matchesItems(inventory.getItem(0), inputs);
    }
    @Override public ItemStack assemble(Container inventory, RegistryAccess access) {
        return inventory.getContainerSize() == 0 ? ItemStack.EMPTY : previewOutput(inventory.getItem(0));
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= components.size() + 1; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return mode == Mode.REPLACE ? result.copy() : previewOutput(displayCentral()); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        out.add(Ingredient.of(displayCentral()));
        components(displayCentral()).forEach(component -> out.add(component.displayIngredient()));
        return out;
    }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return InfusionModule.RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return InfusionModule.RECIPE_TYPE.get(); }
    @Override public boolean isSpecial() { return true; }

    private static ItemStack readStack(JsonObject data) {
        var id = InfusionIngredient.location(GsonHelper.getAsString(data, "item"));
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new JsonSyntaxException("Unknown infusion result " + id));
        int count = GsonHelper.getAsInt(data, "count", 1);
        if (item == Items.AIR || count <= 0 || count > item.getMaxStackSize()) throw new JsonSyntaxException("Invalid infusion result count");
        ItemStack stack = new ItemStack(item, count);
        if (data.has("nbt")) stack.setTag(InfusionIngredient.parseNbt(GsonHelper.getAsString(data, "nbt")));
        return stack;
    }
    public static final class Serializer implements RecipeSerializer<InfusionRecipe> {
        private static final Gson GSON = new Gson();
        @Override public InfusionRecipe fromJson(ResourceLocation id, JsonObject json) { return new InfusionRecipe(id, json); }
        @Override public InfusionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return fromJson(id, JsonParser.parseString(buffer.readUtf(32767)).getAsJsonObject());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, InfusionRecipe recipe) { buffer.writeUtf(GSON.toJson(recipe.data), 32767); }
    }
}
