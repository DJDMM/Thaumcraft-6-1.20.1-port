package thaumcraft.scanning;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** TC6's 0.75 crafting yield, remainder subtraction, and lowest-total recipe choice.
 * Special/NBT recipes, infusion, brewing and arcane recipes require separate resolvers.
 */
final class RecipeAspectResolver {
    private final RecipeManager manager;
    private final RegistryAccess access;
    private Map<ResourceLocation, List<CraftingRecipe>> byOutput;

    RecipeAspectResolver(RecipeManager manager, RegistryAccess access) {
        this.manager = manager;
        this.access = access;
    }

    private void index() {
        if (byOutput != null) return;
        byOutput = new HashMap<>();
        // Delayed until first use: all reload listeners (including recipes and tags) are now finished.
        for (CraftingRecipe recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (recipe.isSpecial() || recipe.getIngredients().size() > 9) continue;
            ItemStack output = recipe.getResultItem(access);
            if (output.isEmpty() || output.hasTag()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(output.getItem());
            byOutput.computeIfAbsent(id, key -> new ArrayList<>()).add(recipe);
        }
        byOutput.values().forEach(recipes -> recipes.sort(Comparator.comparing(r -> r.getId().toString())));
    }

    AspectList derive(ItemStack stack, AspectRegistry.Snapshot data, HashSet<ResourceLocation> path, int depth, AspectRegistry.ResolutionBudget budget) {
        index();
        AspectList best = new AspectList();
        int minimum = Integer.MAX_VALUE;
        for (CraftingRecipe recipe : byOutput.getOrDefault(ForgeRegistries.ITEMS.getKey(stack.getItem()), List.of())) {
            if (!budget.spend()) return new AspectList();
            CraftingContainer input = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(Player player) { return true; }
            }, 3, 3);
            AspectList total = new AspectList();
            int slot = 0;
            boolean known = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                ItemStack[] choices = ingredient.getItems();
                if (choices.length == 0) { slot++; continue; }
                // Preserve the recipe's first representative, as the TC6 algorithm does.
                ItemStack chosen = choices[0];
                if (path.contains(ForgeRegistries.ITEMS.getKey(chosen.getItem()))) { known = false; break; }
                AspectList aspects = data.resolve(chosen, path, depth, budget);
                if (aspects.size() == 0) { known = false; break; }
                total.add(ItemAspectBonuses.apply(chosen, aspects));
                input.setItem(slot++, chosen.copy());
            }
            if (!known || total.size() == 0) continue;
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty()) {
                    AspectList returned = data.resolve(remainder, path, depth, budget);
                    for (Aspect aspect : returned.getAspects()) total.remove(aspect, returned.getAmount(aspect));
                }
            }
            AspectList candidate = new AspectList();
            int count = recipe.getResultItem(access).getCount();
            for (Aspect aspect : total.getAspects()) {
                float value = total.getAmount(aspect) * 0.75F / count;
                if (value < 1 && value > 0.75F) value = 1;
                if ((int) value > 0) candidate.add(aspect, Math.min(500, (int) value));
            }
            if (candidate.visSize() > 0 && candidate.visSize() < minimum) {
                best = candidate;
                minimum = candidate.visSize();
            }
        }
        return best;
    }
}
