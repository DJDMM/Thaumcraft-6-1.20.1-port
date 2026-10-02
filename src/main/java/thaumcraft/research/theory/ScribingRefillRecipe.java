package thaumcraft.research.theory;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class ScribingRefillRecipe extends CustomRecipe {
    private static final TagKey<Item> INK = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("thaumcraft", "ink"));
    public ScribingRefillRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public boolean matches(CraftingContainer input, Level level) {
        int tools = 0, ink = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(TheoryModule.SCRIBING_TOOLS.get())) tools++;
            else if (stack.is(INK)) ink++;
            else return false;
        }
        return tools == 1 && ink == 1;
    }
    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess access) {
        for (int i = 0; i < input.getContainerSize(); i++) {
            if (input.getItem(i).is(TheoryModule.SCRIBING_TOOLS.get())) {
                ItemStack result = input.getItem(i).copyWithCount(1);
                result.setDamageValue(0);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return TheoryModule.REFILL.get(); }
}
