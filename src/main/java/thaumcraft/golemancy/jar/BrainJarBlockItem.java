package thaumcraft.golemancy.jar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

public final class BrainJarBlockItem extends BlockItem {
    public BrainJarBlockItem(Block block) { super(block, new Item.Properties()); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag().contains("xp"))
            tooltip.add(Component.literal(Math.max(0, stack.getTag().getInt("xp")) + " xp").withStyle(ChatFormatting.GREEN));
        super.appendHoverText(stack, level, tooltip, flag);
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new thaumcraft.golemancy.jar.client.BrainJarItemExtension());
    }
}
