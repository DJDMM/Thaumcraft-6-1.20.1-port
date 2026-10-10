package thaumcraft.catalog.blocks;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
public final class CatalogBlockItem extends BlockItem {
    public CatalogBlockItem(Block block) { super(block,new Item.Properties()); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        // Item's constructor calls this before BlockItem assigns its block field.
        // Only builtin/entity models invoke the extension's renderer.
        consumer.accept(new thaumcraft.catalog.blocks.client.CatalogBlockItemExtension());
    }
}
