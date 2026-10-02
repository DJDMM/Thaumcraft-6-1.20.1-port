package thaumcraft.alchemy;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;

public final class SalisMundusRecipe extends CustomRecipe {
    public SalisMundusRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public boolean matches(CraftingContainer input, Level level) {
        int flint = 0, bowl = 0, redstone = 0;
        Set<String> crystals = new HashSet<>();
        for (int i = 0; i < input.getContainerSize(); i++) {
            var stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.FLINT)) flint++;
            else if (stack.is(Items.BOWL)) bowl++;
            else if (stack.is(Items.REDSTONE)) redstone++;
            else {
                // BETA26 RecipeMagicDust compares three distinct contained aspect tags;
                // unlike arcane primal slots, its bytecode has no isPrimal restriction.
                var aspect = AspectCrystalItem.crystalAspect(stack);
                if (aspect == null || !crystals.add(aspect.getTag())) return false;
            }
        }
        return flint == 1 && bowl == 1 && redstone == 1 && crystals.size() == 3;
    }
    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess access) { return new ItemStack(AlchemyModule.SALIS_MUNDUS.get()); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 6; }
    @Override public RecipeSerializer<?> getSerializer() { return AlchemyModule.SALIS_RECIPE.get(); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < input.getContainerSize(); i++) {
            if (input.getItem(i).is(Items.FLINT) || input.getItem(i).is(Items.BOWL)) remaining.set(i, input.getItem(i).copyWithCount(1));
        }
        return remaining;
    }
}
