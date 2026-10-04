package thaumcraft.essentia.centrifuge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.world.aura.AuraManager;

public final class CentrifugeBlock extends BaseEntityBlock {
    public CentrifugeBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CentrifugeBlockEntity(pos,state); }
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos) { return Shapes.empty(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type,CentrifugeModule.CENTRIFUGE.get(),CentrifugeBlockEntity::tick);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return java.util.List.of(new ItemStack(this)); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock()!=next.getBlock() && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity tile && tile.output()!=null) {
            // Original BlockTCTile spills only getEssentiaAmount(UP), so pending input is lost.
            AuraManager.addFlux(server,pos,1);
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
