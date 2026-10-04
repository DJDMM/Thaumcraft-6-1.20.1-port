package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.catalog.blocks.CatalogBlocks;

public final class ThaumatoriumBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final boolean top;
    public ThaumatoriumBlock(Properties properties,boolean top) { super(properties);this.top=top;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    public boolean isTop() { return top; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override public RenderShape getRenderShape(BlockState state) { return top?RenderShape.INVISIBLE:RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return top?new ThaumatoriumTopBlockEntity(pos,state):new ThaumatoriumBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return top?null:createTickerHelper(type,ThaumatoriumModule.BASE.get(),ThaumatoriumBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player.isShiftKeyDown())return InteractionResult.SUCCESS;
        BlockPos base=top?pos.below():pos;
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.hasChunkAt(base)&&level.getBlockEntity(base) instanceof ThaumatoriumBlockEntity tile)NetworkHooks.openScreen(server,tile,base);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return !top; }
    @Override public int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) { return level.getBlockEntity(pos) instanceof ThaumatoriumBlockEntity tile?net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(tile):0; }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder params) { return java.util.List.of(new ItemStack(CatalogBlocks.block("metal_alchemical"))); }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving) {
        if(!top&&!level.isClientSide&&level.hasChunkAt(pos.below())&&!level.getBlockState(pos.below()).is(thaumcraft.alchemy.AlchemyModule.CRUCIBLE.get()))level.setBlockAndUpdate(pos,CatalogBlocks.block("metal_alchemical").defaultBlockState());
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(state.getBlock()!=next.getBlock()) {
            if(!top&&level.getBlockEntity(pos) instanceof ThaumatoriumBlockEntity tile) {
                Containers.dropContents(level,pos,tile);tile.dropPendingOutput();
                // Original BlockTCTile asks getEssentiaAmount(UP), which is zero here: stored mixture is lost, not flux.
            }
            super.onRemove(state,level,pos,next,moving);
            BlockPos other=top?pos.below():pos.above();
            if(!level.isClientSide&&level.hasChunkAt(other)&&level.getBlockState(other).getBlock() instanceof ThaumatoriumBlock partner&&partner.top!=top)level.setBlockAndUpdate(other,CatalogBlocks.block("metal_alchemical").defaultBlockState());
            return;
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
