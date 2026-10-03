package thaumcraft.infusion;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
/** Working altar parts retain builtin matrix item rendering without the catalogue-only tooltip. */
public final class InfusionBlockItem extends BlockItem {
    public InfusionBlockItem(Block block) { super(block, new Item.Properties()); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new thaumcraft.catalog.blocks.client.CatalogBlockItemExtension());
    }
}
