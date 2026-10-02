package thaumcraft.catalog;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;

import javax.annotation.Nullable;
import java.util.List;

/** A faithful visual entry; none of the former item's use actions are simulated. */
public class CatalogItem extends Item {
    private final CatalogModule.Spec spec;

    public CatalogItem(CatalogModule.Spec spec) {
        super(properties(spec));
        this.spec = spec;
    }

    public CatalogModule.Spec spec() { return spec; }
    private static Properties properties(CatalogModule.Spec spec) {
        return spec.id().equals("primordial_pearl") ? new Properties().durability(8) : new Properties().stacksTo(spec.stackLimit());
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        if (spec.id().equals("primordial_pearl")) {
            int damage = stack.getDamageValue();
            return getDescriptionId() + (damage < 3 ? ".pearl" : damage < 6 ? ".nodule" : ".mote");
        }
        return super.getDescriptionId(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (spec.id().equals("crystal_essence") || spec.id().equals("phial_filled") || spec.id().equals("label_filled")) {
            Aspect aspect = CatalogModule.containedAspect(stack);
            if (aspect != null) return Component.translatable(getDescriptionId(stack), aspect.getName());
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return spec.id().equals("enchanted_placeholder") || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!spec.id().equals("jar_brace")) tooltip.add(Component.translatable("thaumcraft.catalog.visual_only").withStyle(ChatFormatting.GRAY));
        Aspect aspect = CatalogModule.containedAspect(stack);
        if (aspect != null) tooltip.add(Component.literal(aspect.getName()).withStyle(ChatFormatting.AQUA));
        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable("thaumcraft.catalog.original_variant", spec.legacyItem(), spec.legacyMetadata())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
