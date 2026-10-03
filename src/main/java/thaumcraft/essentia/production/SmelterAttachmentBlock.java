package thaumcraft.essentia.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

/** A pump/vent faces into its smelter and cannot occupy the smelter's front. */
public final class SmelterAttachmentBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final boolean vent;
    public SmelterAttachmentBlock(Properties properties, boolean vent) {
        super(properties.strength(1, 10).sound(SoundType.METAL)); this.vent = vent;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    public boolean isVent() { return vent; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) { return java.util.List.of(new net.minecraft.world.item.ItemStack(this)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (!clicked.getAxis().isHorizontal()) return null;
        BlockPos parent = context.getClickedPos().relative(clicked.getOpposite());
        BlockState smelter = context.getLevel().getBlockState(parent);
        if (!(smelter.getBlock() instanceof SmelterBlock) || smelter.getValue(SmelterBlock.FACING) == clicked) return null;
        return defaultBlockState().setValue(FACING, clicked.getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state; }
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!vent) return Shapes.block();
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(2,2,8,14,14,16); case WEST -> box(0,2,2,8,14,14);
            case EAST -> box(8,2,2,16,14,14); default -> box(2,2,0,14,14,8);
        };
    }
}
