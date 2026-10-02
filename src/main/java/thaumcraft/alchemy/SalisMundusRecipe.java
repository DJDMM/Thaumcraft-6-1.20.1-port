package thaumcraft.alchemy;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashSet;
import java.util.Set;

public final class SalisMundusRecipe extends CustomRecipe {
    private static final Set<String> PRIMALS = Set.of("aer", "terra", "ignis", "aqua", "ordo", "perditio");
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
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id == null || !id.getNamespace().equals("thaumcraft") || !id.getPath().startsWith("vis_crystal_")) return false;
                String aspect = id.getPath().substring("vis_crystal_".length());
                if (!PRIMALS.contains(aspect) || !crystals.add(aspect)) return false;
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
