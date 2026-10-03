package thaumcraft.essentia.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Working orientation/redstone support for smelter bellows; other Artifice consumers remain separate. */
public final class SmelterBellowsBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED = SmelterBlock.ENABLED;
    public SmelterBellowsBlock(Properties properties) {
        super(properties.strength(1).sound(SoundType.WOOD));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ENABLED, true));
    }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) { return java.util.List.of(new net.minecraft.world.item.ItemStack(this)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ENABLED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()).setValue(ENABLED, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean enabled = !level.hasNeighborSignal(pos);
        if (state.getValue(ENABLED) != enabled) level.setBlock(pos, state.setValue(ENABLED, enabled), 3);
        BlockPos consumer = pos.relative(state.getValue(FACING));
        if (level.hasChunkAt(consumer) && level.getBlockEntity(consumer) instanceof SmelterBlockEntity smelter) smelter.checkNeighbours();
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
