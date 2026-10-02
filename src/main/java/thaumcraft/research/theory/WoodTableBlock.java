package thaumcraft.research.theory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import thaumcraft.research.ResearchEvents;

public final class WoodTableBlock extends Block {
    static final VoxelShape SHAPE = tableShape(12);
    static VoxelShape tableShape(int top) {
        return net.minecraft.world.phys.shapes.Shapes.or(box(0, top, 0, 16, 16, 16),
                box(1, 0, 1, 5, top, 5), box(11, 0, 1, 15, top, 5),
                box(1, 0, 11, 5, top, 15), box(11, 0, 11, 15, top, 15), box(3, 3, 3, 13, 5, 13));
    }
    public WoodTableBlock() { super(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE).strength(2.5F).noOcclusion()); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(TheoryModule.SCRIBING_TOOLS.get())) return InteractionResult.PASS;
        if (!player.mayBuild()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.FAIL;
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        boolean capturing = level.captureBlockSnapshots;
        int start = level.capturedBlockSnapshots.size();
        level.captureBlockSnapshots = true;
        try {
            BlockState next = TheoryModule.TABLE.get().defaultBlockState().setValue(ResearchTableBlock.FACING, player.getDirection());
            if (!level.setBlockAndUpdate(pos, next)) return InteractionResult.FAIL;
            if (ForgeEventFactory.onBlockPlace(player, before, hit.getDirection())
                    || !(level.getBlockEntity(pos) instanceof ResearchTableBlockEntity)) {
                boolean restoring = level.restoringBlockSnapshots;
                level.restoringBlockSnapshots = true;
                try { before.restore(true, false); } finally { level.restoringBlockSnapshots = restoring; }
                return InteractionResult.FAIL;
            }
            level.captureBlockSnapshots = false;
            next.onPlace(level, pos, state, false);
            level.markAndNotifyBlock(pos, level.getChunkAt(pos), state, next, Block.UPDATE_ALL, 512);
            ResearchTableBlockEntity table = (ResearchTableBlockEntity) level.getBlockEntity(pos);
            table.setItem(0, held.copyWithCount(1));
            held.shrink(1);
            player.getInventory().setChanged();
            ResearchEvents.recordCraft(server, new ItemStack(TheoryModule.TABLE_ITEM.get()));
            return InteractionResult.CONSUME;
        } finally {
            if (level.capturedBlockSnapshots.size() > start) level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()).clear();
            level.captureBlockSnapshots = capturing;
        }
    }
}
