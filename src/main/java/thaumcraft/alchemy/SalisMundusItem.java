package thaumcraft.alchemy;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import thaumcraft.research.ResearchEvents;

public final class SalisMundusItem extends Item {
    public SalisMundusItem(Properties properties) { super(properties); }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // TC6 dust acts before the target opens its GUI; sneaking leaves ordinary interaction available.
        return transform(context);
    }

    @Override public InteractionResult useOn(UseOnContext context) { return transform(context); }

    private InteractionResult transform(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (context.getPlayer() == null || !context.getPlayer().mayBuild()) return InteractionResult.FAIL;
        if (context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        InteractionResult infusion = thaumcraft.infusion.InfusionAltarFormation.use(context);
        if (infusion != InteractionResult.PASS) return infusion;
        boolean bookshelf = level.getBlockState(pos).is(Blocks.BOOKSHELF);
        boolean workbench = level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
        if (!level.getBlockState(pos).is(Blocks.CAULDRON) && !bookshelf && !workbench) return InteractionResult.PASS;
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player && player.mayBuild()) {
            var converted = workbench ? thaumcraft.arcane.ArcaneModule.WORKBENCH.get() : AlchemyModule.CRUCIBLE.get();
            var newState = bookshelf ? Blocks.AIR.defaultBlockState() : converted.defaultBlockState();
            ItemStack crafted = bookshelf ? new ItemStack(thaumcraft.research.ResearchModule.THAUMONOMICON.get()) : new ItemStack(converted);
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
            boolean capturing = level.captureBlockSnapshots;
            int snapshotStart = level.capturedBlockSnapshots.size();
            // Forge capture changes the block without notifying clients, running onPlace or
            // updating neighboring shapes. Keep the whole ritual provisional until both
            // placement and book spawning have passed their cancellation hooks.
            level.captureBlockSnapshots = true;
            try {
                if (!level.setBlockAndUpdate(pos, newState)) return InteractionResult.FAIL;
                if (ForgeEventFactory.onBlockPlace(player, before, context.getClickedFace())) {
                    rollback(level, before);
                    return InteractionResult.FAIL;
                }
                if (bookshelf) {
                    ItemEntity book = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crafted.copy());
                    book.setDefaultPickUpDelay();
                    if (!level.addFreshEntity(book)) {
                        rollback(level, before);
                        return InteractionResult.FAIL;
                    }
                }
                // Finalize exactly as Forge's successful placement wrapper does. Disable
                // capture while notifying neighbors so a surrounding ItemStack.useOn
                // wrapper cannot later cancel those updates after the ritual is paid.
                level.captureBlockSnapshots = false;
                newState.onPlace(level, pos, before.getReplacedBlock(), false);
                level.markAndNotifyBlock(pos, level.getChunkAt(pos), before.getReplacedBlock(), newState, Block.UPDATE_ALL, 512);
                if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1F);
                ResearchEvents.recordCraft(player, crafted);
            } finally {
                // We already resolved these snapshots, including any produced by rollback.
                // Preserve snapshots belonging to a surrounding placement operation.
                if (level.capturedBlockSnapshots.size() > snapshotStart)
                    level.capturedBlockSnapshots.subList(snapshotStart, level.capturedBlockSnapshots.size()).clear();
                level.captureBlockSnapshots = capturing;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void rollback(Level level, BlockSnapshot before) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            // capture remains enabled: restoring the target must not break adjacent torches.
            before.restore(true, false);
        } finally {
            level.restoringBlockSnapshots = restoring;
        }
    }
}
