package thaumcraft.essentia;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.*;
import java.util.List;

/** Jar contents and filter use the original BlockJarItem NBT representation. */
public final class EssentiaJarItem extends BlockItem implements IEssentiaContainerItem {
    public EssentiaJarItem(Block block) { super(block, new Item.Properties()); }
    @Override public boolean isBarVisible(ItemStack stack) { return getAspects(stack) != null; }
    @Override public int getBarWidth(ItemStack stack) {
        AspectList list = getAspects(stack);
        return list == null ? 0 : Math.round(13F * Math.min(EssentiaJarBlockEntity.CAPACITY, list.visSize()) / EssentiaJarBlockEntity.CAPACITY);
    }
    @Override public int getBarColor(ItemStack stack) {
        AspectList list = getAspects(stack);
        float fill = list == null ? 0 : Math.min(1F, list.visSize() / 250F);
        return net.minecraft.util.Mth.hsvToRgb(fill / 3F, 1F, 1F);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (!stack.hasTag()) return;
        AspectList list = new AspectList(); list.readFromNBT(stack.getTag());
        for (Aspect type : list.getAspects()) if (list.getAmount(type) > 0)
            lines.add(Component.literal(type.getName() + " × " + list.getAmount(type)).withStyle(ChatFormatting.GRAY));
        Aspect filter = Aspect.getAspect(stack.getTag().getString("AspectFilter"));
        if (filter != null) lines.add(Component.translatable("tooltip.thaumcraft.essentia_filter", filter.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
