package thaumcraft.essentia;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.catalog.CatalogModule;

/**
 * BETA26 ConfigRecipes' per-aspect label recipes as one NBT-aware modern recipe.
 * CraftingEvents returned the full filled phial; a crafting remainder preserves
 * that behavior without growing an input slot or registering a global craft hook.
 */
public final class EssentiaLabelRecipe extends CustomRecipe {
    public EssentiaLabelRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) { return matchedAspect(input) != null; }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess access) {
        Aspect aspect = matchedAspect(input);
        return aspect == null ? ItemStack.EMPTY : CatalogModule.aspectStack("label_filled", aspect, 1);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width > 0 && height > 0 && (long)width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return EssentiaModule.LABEL_RECIPE.get(); }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        boolean valid = matchedAspect(input) != null;
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (valid && stack.is(CatalogModule.ENTRIES.get("phial_filled").get()))
                remaining.set(slot, stack.copyWithCount(1));
            else if (stack.hasCraftingRemainingItem()) remaining.set(slot, stack.getCraftingRemainingItem());
        }
        return remaining;
    }

    @Nullable
    private static Aspect matchedAspect(CraftingContainer input) {
        boolean foundLabel = false;
        Aspect foundAspect = null;
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(CatalogModule.ENTRIES.get("label_blank").get())) {
                if (foundLabel) return null;
                foundLabel = true;
            } else if (stack.is(CatalogModule.ENTRIES.get("phial_filled").get())) {
                if (foundAspect != null) return null;
                foundAspect = validPhialAspect(stack);
                if (foundAspect == null) return null;
            } else return null;
        }
        return foundLabel ? foundAspect : null;
    }

    @Nullable
    private static Aspect validPhialAspect(ItemStack stack) {
        if (!(stack.getItem() instanceof IEssentiaContainerItem container) || !stack.hasTag()) return null;
        // One canonical entry excludes unknown/negative records that the API's safe
        // parser otherwise ignores, while preserving unrelated display/item NBT.
        ListTag raw = stack.getTag().getList("Aspects", Tag.TAG_COMPOUND);
        if (raw.size() != 1) return null;
        var entry = raw.getCompound(0);
        if (!entry.contains("key", Tag.TAG_STRING) || !entry.contains("amount", Tag.TAG_INT)
                || entry.getInt("amount") != 10) return null;
        AspectList contents = container.getAspects(stack);
        if (contents == null || contents.size() != 1 || contents.visSize() != 10) return null;
        Aspect aspect = contents.getAspects()[0];
        return aspect != null && contents.getAmount(aspect) == 10 ? aspect : null;
    }
}
