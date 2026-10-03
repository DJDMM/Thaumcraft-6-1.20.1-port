package thaumcraft.essentia.production;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

public final class AlembicBlock extends BaseEntityBlock {
    public AlembicBlock(Properties properties) { super(properties.sound(SoundType.WOOD)); }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) { return java.util.List.of(new ItemStack(this)); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AlembicBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(2,0,2,14,16,14); }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic)) return 0;
        return (int)Math.floor(alembic.amount() / 128F * 14F) + (alembic.amount() > 0 ? 1 : 0);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        // Existing phial and label item callbacks handle jars and PASS here; reproduce
        // their original alembic branches before the block's sneak/purge behaviour.
        if (held.is(CatalogModule.stack("phial_empty").getItem()) && alembic.amount() >= 10 && alembic.aspect() != null) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            Aspect type = alembic.aspect();
            if (alembic.takeFromContainer(type, 10)) {
                held.shrink(1); giveOrDrop(player, CatalogModule.aspectStack("phial_filled", type, 10), pos);
                inventoryChanged(player); level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, .25F, 1);
            }
            return InteractionResult.SUCCESS;
        }
        if (held.getItem() instanceof thaumcraft.essentia.item.EssentiaLabelItem label) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            AspectList list = label.getAspects(held);
            Aspect template = list == null || list.size() == 0 ? null : list.getAspects()[0];
            if (alembic.applyLabel(player, hit.getDirection(), template)) {
                held.shrink(1); inventoryChanged(player);
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && alembic.filter() != null && hit.getDirection() == alembic.labelFacing()) {
            if (!level.isClientSide && alembic.removeLabel()) {
                Direction side = hit.getDirection();
                level.addFreshEntity(new ItemEntity(level, pos.getX()+.5+side.getStepX()/3D, pos.getY()+.5, pos.getZ()+.5+side.getStepZ()/3D, CatalogModule.stack("label_blank")));
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, 1);
            }
        } else if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide) { alembic.purge(); level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, .5F, 1); }
        }
        // Filled phials NEVER pour into alembics in BETA26 ItemPhial.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void inventoryChanged(Player player) { player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges(); }
    private static void giveOrDrop(Player player, ItemStack stack, BlockPos pos) {
        boolean room = player.getInventory().getFreeSlot() >= 0 || player.getInventory().getSlotWithRemainingSpace(stack) >= 0;
        if (!room || !player.getInventory().add(stack)) player.level().addFreshEntity(new ItemEntity(player.level(), pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, stack));
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic) {
            if (level instanceof ServerLevel server) AuraManager.addFlux(server, pos, alembic.amount());
            alembic.takeFromContainer(alembic.aspect(), alembic.amount());
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
