package thaumcraft.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class InfusionMatrixBlock extends BaseEntityBlock {
    public InfusionMatrixBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new InfusionMatrixBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, InfusionModule.MATRIX.get(), InfusionMatrixBlockEntity::tick);
    }
    public static boolean isCaster(net.minecraft.world.item.ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("thaumcraft") && (id.getPath().equals("caster_basic") || id.getPath().equals("caster_gauntlet"));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isCaster(player.getItemInHand(hand)) || player.isSpectator() || !player.isAlive()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof InfusionMatrixBlockEntity matrix) matrix.useCaster(server);
        // Release's callback returns false; the modern block consumes this successful interaction
        // so future focus casting cannot also run on the same click.
        return InteractionResult.CONSUME;
    }
}
