package thaumcraft.golemancy.press;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

/** Original invisible central press piston; the connected parts are restored by formation. */
public final class GolemPressBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public GolemPressBlock() {
        // Pinned BlockGolemBuilder ctor 0..8 -> BlockTCDevice ctor 0..4 -> BlockTCTile ctor 6..18:
        // setHardness(2), setResistance(20). The inherited old setter*3 / getter5 yields effective12.
        this(Properties.of().strength(2.0f, 12.0f).sound(net.minecraft.world.level.block.SoundType.STONE)
                .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
    }
    public GolemPressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) { return state; }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) { return state; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new GolemPressBlockEntity(position, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, GolemPressRegistry.PRESS_BE.get(), GolemPressBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof GolemPressBlockEntity tile) {
            thaumcraft.research.ResearchNetwork.sync(server);
            NetworkHooks.openScreen(server, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(new ItemStack(Items.PISTON)); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock()) {
            if (!level.isClientSide && !level.restoringBlockSnapshots) {
                if (level.getBlockEntity(pos) instanceof GolemPressBlockEntity tile) Containers.dropContents(level, pos, tile);
                GolemPressFormation.destroy(level, pos, pos);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
