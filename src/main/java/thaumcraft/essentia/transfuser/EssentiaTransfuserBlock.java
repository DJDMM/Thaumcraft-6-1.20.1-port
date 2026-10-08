package thaumcraft.essentia.transfuser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.*;

/** Original six-face baked device, with its pipe attachment opposite its front. */
public final class EssentiaTransfuserBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final boolean filling;

    public EssentiaTransfuserBlock(Properties properties, boolean filling) {
        super(properties);
        this.filling = filling;
        // BETA26 discards withProperty(UP) in both constructors, leaving metadata0/DOWN.
        // Existing saved catalogue states keep their explicitly stored facing.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    public boolean isFilling() { return filling; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    /** BlockTCDevice.rotateBlock cycles the six EnumFacing values, independently of axis. */
    public static BlockState cycleFacing(BlockState state) {
        Direction[] faces = Direction.values();
        return state.setValue(FACING, faces[(state.getValue(FACING).get3DDataValue() + 1) % faces.length]);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EssentiaTransfuserBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EssentiaTransfuserModule.TRANSFUSER.get(), EssentiaTransfuserBlockEntity::tick);
    }
    @Override public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> box(4, 8, 4, 12, 16, 12);
            case UP -> box(4, 0, 4, 12, 8, 12);
            case NORTH -> box(4, 4, 8, 12, 12, 16);
            case SOUTH -> box(4, 4, 0, 12, 12, 8);
            case WEST -> box(8, 4, 4, 16, 12, 12);
            case EAST -> box(0, 4, 4, 8, 12, 12);
        };
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return java.util.List.of(new ItemStack(this)); }
}
