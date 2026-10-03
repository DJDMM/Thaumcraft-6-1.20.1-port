package thaumcraft.auromancy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.CatalogItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.auromancy.focus.FocusStacks;
import java.util.List;

public final class FocusItem extends CatalogItem {
    public FocusItem(CatalogModule.Spec spec) { super(spec); }
    @Override public net.minecraft.world.item.Rarity getRarity(ItemStack stack) { return net.minecraft.world.item.Rarity.RARE; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.thaumcraft.focus.capacity", FocusStacks.maxComplexity(stack)).withStyle(ChatFormatting.GRAY));
        if (FocusStacks.readPlan(stack).isEmpty()) tooltip.add(Component.translatable("tooltip.thaumcraft.focus.blank").withStyle(ChatFormatting.GRAY));
        else addFocusTooltip(stack, tooltip);
    }
    public static void addFocusTooltip(ItemStack stack, List<Component> tooltip) {
        FocusStacks.readPlan(stack).ifPresent(plan -> {
            tooltip.add(Component.translatable("tooltip.thaumcraft.focus.price", plan.castVis(), plan.cooldownTicks() / 20F).withStyle(ChatFormatting.AQUA));
            for(var node:plan.graph().nodes())if(!node.key().equals(thaumcraft.auromancy.focus.FocusNodeRegistry.ROOT)){
                var definition=thaumcraft.auromancy.focus.FocusNodeRegistry.get(node.key());
                var line=Component.translatable(node.key()+".name");
                if(!definition.settings().isEmpty()){
                    line.append(" [");boolean first=true;
                    for(var setting:definition.settings().values()){
                        if(!first)line.append(", ");first=false;
                        int index=setting.values().indexOf(node.settings().get(setting.key()));
                        line.append(Component.translatable(setting.label())).append(": ")
                                .append(Component.translatable(setting.descriptions().get(index)));
                    }
                    line.append("]");
                }
                tooltip.add(line.withStyle(ChatFormatting.DARK_PURPLE));
            }
        });
    }
}
