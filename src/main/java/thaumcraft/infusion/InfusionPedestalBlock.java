package thaumcraft.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** All three release pedestal variants use the same charge state and one-slot interactions. */
public final class InfusionPedestalBlock extends BaseEntityBlock {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);

    public InfusionPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CHARGE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new InfusionPedestalBlockEntity(pos, state); }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !player.isAlive()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof InfusionPedestalBlockEntity pedestal)
                || !level.getServer().isSameThread()) return InteractionResult.PASS;
        // Release quirk: insertion permission tests main hand, but takes the interacting hand's item.
        if (pedestal.isEmpty() && !player.getMainHandItem().isEmpty()) {
            ItemStack held = player.getItemInHand(hand);
            pedestal.setItem(0, held);
            held.shrink(1);
            if (held.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
            player.getInventory().setChanged();
            pickupSound(level, pos, 1.6F);
            return InteractionResult.CONSUME;
        }
        if (!pedestal.isEmpty()) {
            ItemStack removed = pedestal.removeItemNoUpdate(0);
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + player.getEyeHeight() / 2,
                    player.getZ(), removed));
            pickupSound(level, pos, 1.5F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void pickupSound(Level level, BlockPos pos, float factor) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .2F,
                ((level.random.nextFloat() - level.random.nextFloat()) * .7F + 1) * factor);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof InfusionPedestalBlockEntity pedestal) {
            Containers.dropContents(level, pos, pedestal);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, next, moved);
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            for (Direction direction : Direction.Plane.HORIZONTAL)
                InfusionInlayBlock.updateCharge(level, pos.relative(direction));
        }
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !state.is(old.getBlock())) {
            InfusionInlayBlock.updateCharge(level, pos);
            for (Direction direction : Direction.Plane.HORIZONTAL)
                InfusionInlayBlock.updateCharge(level, pos.relative(direction));
        }
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                          BlockPos from, boolean moved) {
        if (!level.isClientSide) InfusionInlayBlock.updateCharge(level, pos);
    }

    /** Release symmetry depends only on occupancy, never on the contents' identity. */
    public static boolean hasSymmetryPenalty(Level level, BlockPos first, BlockPos opposite) {
        return level.getBlockEntity(first) instanceof InfusionPedestalBlockEntity a
                && level.getBlockEntity(opposite) instanceof InfusionPedestalBlockEntity b
                && a.isEmpty() != b.isEmpty();
    }
}
