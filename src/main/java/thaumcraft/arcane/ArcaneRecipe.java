package thaumcraft.arcane;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.alchemy.AspectCrystalItem;

/** Datapack recipe: vanilla shaped/shapeless fields plus vis, crystals and research. */
public final class ArcaneRecipe implements Recipe<Container> {
    private final ShapedRecipe shape;
    private final ShapelessRecipe shapeless;
    private final boolean preserveJar;
    private final int vis;
    private final int[] crystals;
    private final String research;

    public ArcaneRecipe(ShapedRecipe shape, int vis, int[] crystals, String research) {
        this(shape, null, vis, crystals, research, false);
    }
    private ArcaneRecipe(ShapedRecipe shape, ShapelessRecipe shapeless, int vis, int[] crystals, String research, boolean preserveJar) {
        this.shape = shape; this.shapeless = shapeless; this.preserveJar = preserveJar;
        this.vis = vis; this.crystals = crystals.clone(); this.research = research;
    }
    public boolean shapeless() { return shapeless != null; }
    public int gridWidth() { return shapeless() ? Math.min(3, getIngredients().size()) : shape.getWidth(); }
    public int gridHeight() { return shapeless() ? Math.max(1, (getIngredients().size() + 2) / 3) : shape.getHeight(); }
    public int vis() { return vis; }
    public String research() { return research; }
    public int crystalCost(int primal) { return crystals[primal]; }
    public boolean unlocked(ServerPlayer player) { return research.isEmpty() || KnowledgeStore.get(player).knowsResearch(research); }
    public boolean hasCrystals(Container inventory) {
        if (inventory.getContainerSize() < 15) return false;
        for (int i = 0; i < 6; i++) {
            if (crystals[i] == 0) continue;
            ItemStack stack = inventory.getItem(9 + i);
            if (!AspectCrystalItem.matchesPrimal(stack, ArcaneModule.PRIMALS[i]) || stack.getCount() < crystals[i]) return false;
        }
        return true;
    }
    @Override public boolean matches(Container inventory, Level level) {
        if (inventory.getContainerSize() < 9) return false;
        if (shapeless()) {
            java.util.List<ItemStack> inputs = new java.util.ArrayList<>();
            for (int slot = 0; slot < 9; slot++) if (!inventory.getItem(slot).isEmpty()) inputs.add(inventory.getItem(slot));
            return inputs.size() == shapeless.getIngredients().size()
                    && net.minecraftforge.common.util.RecipeMatcher.findMatches(inputs, shapeless.getIngredients()) != null;
        }
        for (int x = 0; x <= 3 - shape.getWidth(); x++) for (int y = 0; y <= 3 - shape.getHeight(); y++) {
            if (matchesAt(inventory, x, y, false) || matchesAt(inventory, x, y, true)) return true;
        }
        return false;
    }
    private boolean matchesAt(Container inventory, int offsetX, int offsetY, boolean mirror) {
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            int px = x - offsetX, py = y - offsetY;
            Ingredient ingredient = Ingredient.EMPTY;
            if (px >= 0 && py >= 0 && px < shape.getWidth() && py < shape.getHeight()) {
                if (mirror) px = shape.getWidth() - px - 1;
                ingredient = shape.getIngredients().get(px + py * shape.getWidth());
            }
            if (!ingredient.test(inventory.getItem(x + y * 3))) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(Container inventory, RegistryAccess access) {
        ItemStack result = getResultItem(access).copy();
        if (preserveJar) {
            // Original ShapedArcaneVoidJar copies the whole normal jar tag, not only Aspects.
            for (int slot = 0; slot < Math.min(9, inventory.getContainerSize()); slot++) {
                ItemStack input = inventory.getItem(slot);
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem())
                        .equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "jar_normal"))) {
                    result.setTag(input.hasTag() ? input.getTag().copy() : null);
                    break;
                }
            }
        }
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return shapeless() ? shapeless.canCraftInDimensions(width, height) : shape.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return shapeless() ? shapeless.getResultItem(access) : shape.getResultItem(access); }
    @Override public NonNullList<Ingredient> getIngredients() { return shapeless() ? shapeless.getIngredients() : shape.getIngredients(); }
    @Override public ResourceLocation getId() { return shapeless() ? shapeless.getId() : shape.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return ArcaneModule.RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ArcaneModule.RECIPE_TYPE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<ArcaneRecipe> {
        private final ShapedRecipe.Serializer shaped = new ShapedRecipe.Serializer();
        private final ShapelessRecipe.Serializer shapeless = new ShapelessRecipe.Serializer();
        @Override public ArcaneRecipe fromJson(ResourceLocation id, JsonObject json) {
            int vis = GsonHelper.getAsInt(json, "vis", 0);
            if (vis < 0 || vis > 32767) throw new JsonSyntaxException("Arcane vis must be between 0 and 32767");
            int[] costs = new int[6];
            JsonObject crystals = GsonHelper.getAsJsonObject(json, "crystals", new JsonObject());
            for (String key : crystals.keySet()) {
                if (java.util.Arrays.stream(ArcaneModule.PRIMALS).noneMatch(key::equals)) throw new JsonSyntaxException("Unknown primal crystal: " + key);
            }
            for (int i = 0; i < 6; i++) {
                costs[i] = GsonHelper.getAsInt(crystals, ArcaneModule.PRIMALS[i], 0);
                if (costs[i] < 0 || costs[i] > 64) throw new JsonSyntaxException("Arcane crystal costs must be between 0 and 64");
            }
            boolean isShapeless = json.has("ingredients");
            if (isShapeless && json.has("pattern")) throw new JsonSyntaxException("Arcane recipe cannot be both shaped and shapeless");
            return new ArcaneRecipe(isShapeless ? null : shaped.fromJson(id, json),
                    isShapeless ? shapeless.fromJson(id, json) : null, vis, costs,
                    GsonHelper.getAsString(json, "research", ""), GsonHelper.getAsBoolean(json, "preserve_jar", false));
        }
        @Override public ArcaneRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            boolean isShapeless = buffer.readBoolean();
            ShapedRecipe base = isShapeless ? null : shaped.fromNetwork(id, buffer);
            ShapelessRecipe loose = isShapeless ? shapeless.fromNetwork(id, buffer) : null;
            int vis = buffer.readVarInt();
            int[] costs = new int[6];
            for (int i = 0; i < 6; i++) costs[i] = buffer.readVarInt();
            String research = buffer.readUtf(256);
            return new ArcaneRecipe(base, loose, vis, costs, research, buffer.readBoolean());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, ArcaneRecipe recipe) {
            buffer.writeBoolean(recipe.shapeless());
            if (recipe.shapeless()) shapeless.toNetwork(buffer, recipe.shapeless); else shaped.toNetwork(buffer, recipe.shape);
            buffer.writeVarInt(recipe.vis);
            for (int cost : recipe.crystals) buffer.writeVarInt(cost);
            buffer.writeUtf(recipe.research, 256);
            buffer.writeBoolean(recipe.preserveJar);
        }
    }
}
