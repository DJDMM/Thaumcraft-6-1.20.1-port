package thaumcraft.essentia.transport;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Operating devices have no catalogue-placeholder tooltip. */
public final class TubeBlockItem extends BlockItem {
    public TubeBlockItem(Block block) { super(block, new Item.Properties()); }
}
