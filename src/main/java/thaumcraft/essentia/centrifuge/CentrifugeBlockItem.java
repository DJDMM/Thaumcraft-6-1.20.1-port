package thaumcraft.essentia.centrifuge;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

/** Original builtin/entity item model without the catalogue's visual-only tooltip. */
public final class CentrifugeBlockItem extends BlockItem {
    public CentrifugeBlockItem(Block block) { super(block,new Item.Properties()); }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new thaumcraft.essentia.centrifuge.client.CentrifugeItemExtension());
    }
}
