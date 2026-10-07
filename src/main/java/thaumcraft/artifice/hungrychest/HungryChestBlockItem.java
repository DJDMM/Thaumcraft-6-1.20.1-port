package thaumcraft.artifice.hungrychest;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class HungryChestBlockItem extends BlockItem {
    public HungryChestBlockItem(Block block,Properties properties) {super(block,properties);}
    @Override public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {consumer.accept(new thaumcraft.artifice.hungrychest.client.HungryChestItemRenderer.Extension());}
}
