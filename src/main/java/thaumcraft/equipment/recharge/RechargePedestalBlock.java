package thaumcraft.equipment.recharge;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.items.IRechargable;

public final class RechargePedestalBlock extends BaseEntityBlock {
    public RechargePedestalBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new RechargePedestalBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,RechargeModule.PEDESTAL.get(),RechargePedestalBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RechargePedestalBlockEntity pedestal) || player.isSpectator()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack held=player.getItemInHand(hand);
        if(pedestal.isEmpty() && held.getItem() instanceof IRechargable) {
            // Validate the actual hand; BETA26 accidentally checked main hand even for an offhand click.
            pedestal.setItem(0,held.split(1));
            player.getInventory().setChanged();
        } else if (!pedestal.isEmpty()) {
            ItemStack removed=pedestal.removeItemNoUpdate(0);
            level.addFreshEntity(new ItemEntity(level,player.getX(),player.getY()+player.getEyeHeight()/2,player.getZ(),removed));
        } else return InteractionResult.PASS;
        level.playSound(null,pos,SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,.2F,((level.random.nextFloat()-level.random.nextFloat())*.7F+1)*1.5F);
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof RechargePedestalBlockEntity pedestal) {
            Containers.dropContents(level,pos,pedestal);
            level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,next,moved);
    }
}
