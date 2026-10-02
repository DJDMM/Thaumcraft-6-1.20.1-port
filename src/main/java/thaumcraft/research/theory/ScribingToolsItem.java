package thaumcraft.research.theory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

/** Ink is spent explicitly by the table, so exhausted tools stay available for refilling. */
public final class ScribingToolsItem extends Item {
    public ScribingToolsItem() { super(new Item.Properties().durability(100)); }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("thaumcraft.theory.ink_remaining", Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage()));
    }
}
