package thaumcraft.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class CrucibleBlock extends BaseEntityBlock {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final BooleanProperty BOILING = BooleanProperty.create("boiling");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16),
            Block.box(0, 4, 0, 2, 16, 16), Block.box(14, 4, 0, 16, 16, 16),
            Block.box(2, 4, 0, 14, 16, 2), Block.box(2, 4, 14, 14, 16, 16));
    public CrucibleBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.CAULDRON).strength(2F).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FILLED, false).setValue(BOILING, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FILLED, BOILING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrucibleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AlchemyModule.CRUCIBLE_TILE.get(), CrucibleBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!player.mayBuild()) return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.WATER_BUCKET)) {
            if (crucible.fillWater() && !player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        } else if (player.isShiftKeyDown() && held.isEmpty()) {
            crucible.empty();
        } else if (held.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.thaumcraft.crucible", crucible.water(), crucible.heat(), crucible.aspects().visSize()), true);
        } else if (player instanceof ServerPlayer serverPlayer) {
            // One item per click; creative use supplies a copy without mutating the held stack.
            ItemStack offered = player.getAbilities().instabuild ? held.copyWithCount(1) : held;
            crucible.consume(offered, serverPlayer);
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible && !level.isClientSide) crucible.discardContents();
        super.onRemove(state, level, pos, newState, moving);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BOILING)) level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.8, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
