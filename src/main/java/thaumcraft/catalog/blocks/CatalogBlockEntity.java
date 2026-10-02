package thaumcraft.catalog.blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
/** Storage-free anchor for original special block visuals. */
public final class CatalogBlockEntity extends BlockEntity {
    public CatalogBlockEntity(BlockPos pos,BlockState state) { super(CatalogBlocks.VISUAL_TILE.get(),pos,state); }
    @Override public AABB getRenderBoundingBox() {
        if(getBlockState().getBlock() instanceof CatalogBlock block && block.catalogId().startsWith("banner_")) {
            BlockPos pos=getBlockPos();
            // Original TileBanner bounds include the cloth and both floor/wall placements.
            return new AABB(pos.getX(),pos.getY()-1,pos.getZ(),pos.getX()+1,pos.getY()+2,pos.getZ()+1);
        }
        return super.getRenderBoundingBox();
    }
}
