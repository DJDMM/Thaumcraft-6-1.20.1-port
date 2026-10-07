package thaumcraft.artifice.hungrychest;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Always a single 27-slot chest; opening is not blocked by a lid obstruction. */
public final class HungryChestBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE=box(1,0,1,15,14,15);
    public HungryChestBlock(Properties properties) {super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState state,Rotation rotation) {return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror) {return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState state) {return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return new HungryChestBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide?createTickerHelper(type,HungryChestModule.HUNGRY_CHEST.get(),ChestBlockEntity::lidAnimateTick):null;
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {return SHAPE;}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos) {return Shapes.empty();}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest)player.openMenu(chest);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) {
        if(!level.isClientSide&&entity instanceof ItemEntity item&&!item.isRemoved()&&level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest)chest.consume(item);
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) {return true;}
    @Override public int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) {return level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest?AbstractContainerMenu.getRedstoneSignalFromContainer(chest):0;}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest) {Containers.dropContents(level,pos,chest);level.updateNeighbourForOutputSignal(pos,this);}
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {if(level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest)chest.recheckOpen();}
    @Override public boolean triggerEvent(BlockState state,Level level,BlockPos pos,int event,int value) {super.triggerEvent(state,level,pos,event,value);return level.getBlockEntity(pos) instanceof HungryChestBlockEntity chest&&chest.triggerEvent(event,value);}
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {return java.util.List.of(new ItemStack(this));}
}
