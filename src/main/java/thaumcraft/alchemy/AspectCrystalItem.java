package thaumcraft.alchemy;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.WorldModule;

/** BETA26's crystal_essence: one contained aspect, represented by the original Aspects NBT. */
public final class AspectCrystalItem extends Item implements IEssentiaContainerItem {
    public AspectCrystalItem(CatalogModule.Spec spec) {
        super(new Properties().stacksTo(spec.stackLimit()));
    }

    /** Equivalent to ThaumcraftApiHelper.makeCrystal; stack count never changes the per-item amount. */
    public static ItemStack create(Aspect aspect, int count) {
        if (aspect == null || count <= 0) return ItemStack.EMPTY;
        ItemStack result = CatalogModule.stack("crystal_essence");
        if (!result.isEmpty()) {
            result.setCount(count);
            ((IEssentiaContainerItem) result.getItem()).setAspects(result, new AspectList().add(aspect, 1));
        }
        return result;
    }

    public static ItemStack create(Aspect aspect) { return create(aspect, 1); }

    /** Acquisition uses the item class, as BETA26 does, even before its contents initialize. */
    public static boolean isCrystal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getItem() instanceof AspectCrystalItem
                || WorldModule.VIS_CRYSTALS.values().stream().anyMatch(item -> stack.is(item.get())));
    }

    /** Original single-aspect NBT plus compatibility with crystals saved by the earlier port. */
    public static Aspect crystalAspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof AspectCrystalItem crystal) {
            AspectList contents = crystal.getAspects(stack);
            // BETA26 reads the first entry and can crash on uninitialized crystals. Reject
            // malformed/multi-aspect NBT rather than letting it impersonate a primal slot.
            return contents != null && contents.size() == 1 ? contents.getAspects()[0] : null;
        }
        for (var entry : WorldModule.VIS_CRYSTALS.entrySet())
            if (stack.is(entry.getValue().get())) return Aspect.getAspect(entry.getKey());
        return null;
    }

    public static Aspect primalAspect(ItemStack stack) {
        Aspect aspect = crystalAspect(stack);
        return aspect != null && aspect.isPrimal() ? aspect : null;
    }

    public static boolean matchesPrimal(ItemStack stack, String tag) {
        Aspect aspect = primalAspect(stack);
        return aspect != null && aspect.getTag().equals(tag);
    }

    @Override public Component getName(ItemStack stack) {
        AspectList contents = getAspects(stack);
        return contents == null ? super.getName(stack)
                : Component.translatable(getDescriptionId(stack), contents.getAspects()[0].getName());
    }

    private void initialize(ItemStack stack, Level level) {
        // BETA26 checks for any compound, so an existing empty/custom compound stays untouched.
        if (level.isClientSide || stack.getTag() != null) return;
        Aspect[] choices = Aspect.aspects.values().toArray(new Aspect[0]);
        if (choices.length > 0) setAspects(stack, new AspectList().add(choices[level.random.nextInt(choices.length)], 1));
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        initialize(stack, level);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {
        initialize(stack, level);
        super.onCraftedBy(stack, level, player);
    }
}
