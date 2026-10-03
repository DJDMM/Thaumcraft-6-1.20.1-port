package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.network.NetworkHooks;

public final class FocalManipulatorBlock extends BaseEntityBlock {
    public FocalManipulatorBlock(BlockBehaviour.Properties properties) { super(properties.noOcclusion().sound(SoundType.STONE)); }
    // BETA26 marks the table non-full for rendering but inherits the ordinary full collision box.
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FocalManipulatorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, FocalManipulatorModule.TABLE.get(), FocalManipulatorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof FocalManipulatorBlockEntity table) {
            if (player instanceof ServerPlayer server) NetworkHooks.openScreen(server, table, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.restoringBlockSnapshots && !level.isClientSide
                && level.getBlockEntity(pos) instanceof FocalManipulatorBlockEntity table) {
            Containers.dropContents(level, pos, table); table.clearContent();
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
