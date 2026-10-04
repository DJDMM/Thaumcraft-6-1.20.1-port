package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Mnemonic matrix points toward either machine half; each valid attachment adds two recipes. */
public final class BrainBoxBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    private static final VoxelShape SHAPE=Block.box(3,3,3,13,13,13);
    public BrainBoxBlock(Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPE; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        // BETA26's ordinary Block item drop; the old visual catalogue has no gameplay loot table.
        return java.util.List.of(new net.minecraft.world.item.ItemStack(this));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward=context.getClickedFace().getOpposite();BlockState state=defaultBlockState().setValue(FACING,toward);
        BlockPos host=context.getClickedPos().relative(toward);
        if(!context.getLevel().hasChunkAt(host))return null;
        BlockState machine=context.getLevel().getBlockState(host);
        return machine.getBlock() instanceof ThaumatoriumBlock&&machine.getValue(ThaumatoriumBlock.FACING)!=context.getClickedFace()?state:null;
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving) {
        BlockPos host=pos.relative(state.getValue(FACING));
        if(!level.isClientSide&&level.hasChunkAt(host)&&!(level.getBlockState(host).getBlock() instanceof ThaumatoriumBlock)) {dropResources(state,level,pos);level.removeBlock(pos,false);}
    }
}
