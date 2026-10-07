package thaumcraft.golemancy.levitator;

import net.minecraft.core.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.essentia.transport.EssentiaTransportModule;

public final class LevitatorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED=BooleanProperty.create("enabled");
    public LevitatorBlock(Properties properties) {super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(ENABLED,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(FACING,ENABLED);}
    @Override public RenderShape getRenderShape(BlockState state) {return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {return new LevitatorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return createTickerHelper(type,LevitatorModule.LEVITATOR.get(),LevitatorBlockEntity::tick);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The original uses the placer's position/eye height, rather than modern look-pitch order.
        Direction direction=context.getPlayer()==null?context.getClickedFace():placementFacing(context.getClickedPos(),context.getPlayer());
        if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown())direction=direction.getOpposite();
        return defaultBlockState().setValue(FACING,direction).setValue(ENABLED,!context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }
    public static Direction placementFacing(BlockPos pos,Player player) {
        if(Math.abs(player.getX()-(pos.getX()+.5))<2&&Math.abs(player.getZ()-(pos.getZ()+.5))<2) {
            double eye=player.getY()+player.getEyeHeight();if(eye-pos.getY()>2)return Direction.UP;if(pos.getY()-eye>0)return Direction.DOWN;
        }
        return player.getDirection().getOpposite();
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation) {return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror) {return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    private void updateSignal(Level level,BlockPos pos,BlockState state) {
        if(!level.isClientSide&&state.getValue(ENABLED)==level.hasNeighborSignal(pos))level.setBlock(pos,state.setValue(ENABLED,!level.hasNeighborSignal(pos)),3);
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {super.onPlace(state,level,pos,old,moving);updateSignal(level,pos,state);}
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighbor,boolean moving) {updateSignal(level,pos,state);}
    public static AABB body(Direction face) {
        return new AABB(face.getStepX()>0?.125:0,face.getStepY()>0?.125:0,face.getStepZ()>0?.125:0,
                face.getStepX()<0?.875:1,face.getStepY()<0?.875:1,face.getStepZ()<0?.875:1);
    }
    public static AABB button(Direction face) {
        return switch(face) {
            case UP->new AABB(.375,.0625,.375,.625,.125,.625);
            case DOWN->new AABB(.375,.875,.375,.625,.9375,.625);
            case EAST->new AABB(.0625,.375,.375,.125,.625,.625);
            case WEST->new AABB(.875,.375,.375,.9375,.625,.625);
            case SOUTH->new AABB(.375,.375,.0625,.625,.625,.125);
            case NORTH->new AABB(.375,.375,.875,.625,.625,.9375);
        };
    }
    public static boolean targetsButton(BlockState state,BlockPos pos,Player player) {
        var start=player.getEyePosition();double reach=player.getBlockReach();
        return button(state.getValue(FACING)).move(pos).clip(start,start.add(player.getLookAngle().scale(reach))).isPresent();
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof Player player&&targetsButton(state,pos,player))return Shapes.create(button(state.getValue(FACING)));
        return Shapes.create(body(state.getValue(FACING)));
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {return Shapes.create(body(state.getValue(FACING)));}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos) {return Shapes.empty();}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        // The original retraces its tiny rear button and does not cycle when the large body is clicked.
        if(!targetsButton(state,pos,player)||!(level.getBlockEntity(pos) instanceof LevitatorBlockEntity tile))return InteractionResult.PASS;
        if(!level.isClientSide) {tile.increaseRange(player);level.playSound(null,pos,EssentiaTransportModule.KEY.get(),SoundSource.BLOCKS,.5F,1);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {return java.util.List.of(new ItemStack(this));}
}
